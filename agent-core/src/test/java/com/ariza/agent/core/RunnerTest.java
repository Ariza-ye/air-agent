package com.ariza.agent.core;

import com.ariza.agent.core.model.*;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tool.ToolResult;
import com.ariza.agent.core.tracing.Span;
import com.ariza.agent.core.tracing.TraceEvent;
import com.ariza.agent.core.tracing.TraceEventType;
import com.ariza.agent.core.tracing.TraceExporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author ariza
 */
class RunnerTest {
    /**
     * 验证运行器会将模型文本和实际运行轮次写入最终结果。
     */
    @Test
    void returnsModelText() {
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test").build();
        RunResult result = new Runner(request -> ModelResponse.text("ok")).runAgent(agent, "hello", null);
        assertEquals("ok", result.finalOutput());
        assertEquals(1, result.turns());
    }

    @Test
    void executesToolCallsUntilModelReturnsText() {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger calls = new AtomicInteger();
        Tool tool = new Tool() {
            public String name() {
                return "lookup";
            }

            public String description() {
                return "lookup";
            }

            public JsonNode inputSchema() {
                return mapper.createObjectNode();
            }

            public ToolResult call(JsonNode arguments, RunContext context) {
                context.attributes().put("called", true);
                return ToolResult.success(mapper.valueToTree("result"));
            }
        };
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test")
                .tools(List.of(tool)).build();

        ModelClient client = request -> {
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse("", List.of(
                        new ToolCall("call-1", "item-1", "lookup", mapper.createObjectNode())),
                        new ModelContinuation("test", "continuation"), null, null,
                        new ModelUsage(10, 2, 12, 4, 1));
            }
            assertEquals(1, request.input().size());
            ToolOutput output = (ToolOutput) request.input().get(0);
            assertEquals("call-1", output.callId());
            assertEquals("\"result\"", output.output());
            return new ModelResponse("done", List.of(), null, null, null,
                    new ModelUsage(8, 3, 11, 2, 0));
        };

        RunResult result = new Runner(client, 3, null).runAgent(agent, "hello", null);

        assertEquals("done", result.finalOutput());
        assertEquals(2, result.turns());
        assertEquals(new ModelUsage(18, 5, 23, 6, 1), result.usage());
    }

    /**
     * 验证同名工具的多次调用耗时都会被记录。
     */
    @Test
    void recordsEveryDurationForRepeatedToolCalls() {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger modelCalls = new AtomicInteger();
        Tool tool = new Tool() {
            public String name() {
                return "lookup";
            }

            public String description() {
                return "lookup";
            }

            public JsonNode inputSchema() {
                return mapper.createObjectNode();
            }

            public ToolResult call(JsonNode arguments, RunContext context) {
                return ToolResult.success(mapper.getNodeFactory().textNode("result"));
            }
        };
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test")
                .tools(List.of(tool)).build();
        ModelClient client = request -> modelCalls.getAndIncrement() == 0
                ? new ModelResponse("", List.of(
                new ToolCall("call-1", "item-1", "lookup", mapper.createObjectNode()),
                new ToolCall("call-2", "item-2", "lookup", mapper.createObjectNode())))
                : ModelResponse.text("done");

        Logger runnerLogger = Logger.getLogger(Runner.class.getName());
        AtomicReference<String> logMessage = new AtomicReference<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logMessage.set(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        runnerLogger.addHandler(handler);
        try {
            new Runner(client, 3, null).runAgent(agent, "hello", "session-1");
        } finally {
            runnerLogger.removeHandler(handler);
        }

        assertNotNull(logMessage.get());
        assertTrue(logMessage.get().matches("sessionId:session-1,工具调用耗时（毫秒）: \\{lookup=\\[\\d+, \\d+\\]\\}"));
    }

    /**
     * 验证工具执行抛出运行时异常时不会中断整个运行，而是把失败原因转给模型继续。
     */
    @Test
    void convertsToolRuntimeExceptionToFailure() {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger modelCalls = new AtomicInteger();
        Tool tool = new Tool() {
            public String name() {
                return "boom";
            }

            public String description() {
                return "boom";
            }

            public JsonNode inputSchema() {
                return mapper.createObjectNode();
            }

            public ToolResult call(JsonNode arguments, RunContext context) {
                throw new IllegalStateException("磁盘爆炸");
            }
        };
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test")
                .tools(List.of(tool)).build();
        ModelClient client = request -> {
            if (modelCalls.getAndIncrement() == 0) {
                return new ModelResponse("", List.of(
                        new ToolCall("call-1", "item-1", "boom", mapper.createObjectNode())));
            }
            assertEquals(1, request.input().size());
            ToolOutput output = (ToolOutput) request.input().get(0);
            assertEquals("call-1", output.callId());
            assertTrue(output.output().contains("磁盘爆炸"));
            return ModelResponse.text("已处理工具失败");
        };

        RunResult result = new Runner(client, 3, null).runAgent(agent, "hello", null);

        assertEquals("已处理工具失败", result.finalOutput());
        assertEquals(2, result.turns());
    }

    @Test
    void rejectsUnknownTool() {
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test").build();
        ModelClient client = request -> new ModelResponse("", List.of(
                new ToolCall("call-1", "item-1", "missing", null)));

        assertThrows(AgentRunException.class,
                () -> new Runner(client).runAgent(agent, "hello", null));
    }

    /**
     * 验证达到最大轮次时禁用工具并让模型生成最终总结。
     */
    @Test
    void summarizesKnownInformationWhenMaxTurnsReached() {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        Tool tool = new Tool() {
            public String name() {
                return "loop";
            }

            public String description() {
                return "loop";
            }

            public JsonNode inputSchema() {
                return mapper.createObjectNode();
            }

            public ToolResult call(JsonNode arguments, RunContext context) {
                toolCalls.incrementAndGet();
                return ToolResult.success(mapper.getNodeFactory().textNode("ok"));
            }
        };
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test")
                .tools(List.of(tool)).build();
        ModelContinuation continuation = new ModelContinuation("test", "continuation");
        ModelClient client = request -> {
            if (modelCalls.getAndIncrement() == 0) {
                return new ModelResponse("", List.of(
                        new ToolCall("call-1", "item-1", "loop", mapper.createObjectNode())),
                        continuation, null, null);
            }
            assertEquals(List.of(), request.tools());
            assertEquals(continuation, request.continuation());
            assertEquals(2, request.input().size());
            ToolOutput skippedOutput = (ToolOutput) request.input().get(0);
            assertEquals("call-1", skippedOutput.callId());
            assertEquals("该工具调用未执行，请基于已有信息完成回答。", skippedOutput.output());
            UserInput summaryInstruction = (UserInput) request.input().get(1);
            assertTrue(summaryInstruction.text().contains("不要提及工具调用轮次或内部限制"));
            return ModelResponse.text("基于已有信息的总结");
        };

        RunResult result = new Runner(client, 1, null).runAgent(agent, "hello", null);

        assertEquals("基于已有信息的总结", result.finalOutput());
        assertEquals(2, result.turns());
        assertEquals(2, modelCalls.get());
        assertEquals(0, toolCalls.get());
    }

    /**
     * 验证模型未生成总结文本时保留达到上限前已经生成的文本。
     */
    @Test
    void preservesExistingTextWhenSummaryIsEmpty() {
        AtomicInteger modelCalls = new AtomicInteger();
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test").build();
        ModelClient client = request -> modelCalls.getAndIncrement() == 0
                ? new ModelResponse("阶段结果", List.of(
                new ToolCall("call-1", "item-1", "unused", new ObjectMapper().createObjectNode())))
                : ModelResponse.text("");

        RunResult result = new Runner(client, 1, null).runAgent(agent, "hello", null);

        assertEquals("阶段结果", result.finalOutput());
        assertEquals(2, result.turns());
    }

    /**
     * 验证启用追踪后会导出运行、模型和工具跨度，并保持正确父子关系。
     */
    @Test
    void exportsRunModelAndToolSpans() {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger modelCalls = new AtomicInteger();
        List<Span> spans = new ArrayList<>();
        Tool tool = new Tool() {
            public String name() {
                return "lookup";
            }

            public String description() {
                return "lookup";
            }

            public JsonNode inputSchema() {
                return mapper.createObjectNode();
            }

            public ToolResult call(JsonNode arguments, RunContext context) {
                return ToolResult.success(mapper.getNodeFactory().textNode("result"));
            }
        };
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test")
                .tools(List.of(tool)).build();
        ModelClient client = request -> modelCalls.getAndIncrement() == 0
                ? new ModelResponse("", List.of(
                new ToolCall("call-1", "item-1", "lookup", mapper.createObjectNode())))
                : ModelResponse.text("done");

        RunResult result = new Runner(client, null, spans::add)
                .runAgent(agent, "hello", null);

        assertEquals("done", result.finalOutput());
        assertEquals(List.of("model.call", "tool.call", "model.call", "agent.run"),
                spans.stream().map(Span::name).toList());
        Span rootSpan = spans.get(3);
        assertNull(rootSpan.parentSpanId());
        assertEquals(result.runId(), rootSpan.traceId());
        assertTrue(spans.subList(0, 3).stream()
                .allMatch(span -> rootSpan.spanId().equals(span.parentSpanId())));

        Span firstModelSpan = spans.get(0);
        assertEquals(List.of(new UserInput("hello")), firstModelSpan.attributes().get("model.input"));
        assertEquals(List.of("lookup"), firstModelSpan.attributes().get("model.tool_names"));
        assertEquals(1, firstModelSpan.attributes().get("tool_calls"));

        Span toolSpan = spans.get(1);
        assertEquals("lookup", toolSpan.attributes().get("tool.name"));
        assertEquals("call-1", toolSpan.attributes().get("tool.call_id"));
        assertEquals(1, toolSpan.attributes().get("turn"));
        assertEquals(0, toolSpan.attributes().get("tool.index"));
        assertEquals(mapper.createObjectNode(), toolSpan.attributes().get("tool.arguments"));
        assertEquals(mapper.getNodeFactory().textNode("result"), toolSpan.attributes().get("tool.output"));
    }

    /**
     * 验证找不到模型请求的工具时仍会导出失败工具跨度。
     */
    @Test
    void exportsFailedSpanForUnknownTool() {
        List<Span> spans = new ArrayList<>();
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test").build();
        ModelClient client = request -> new ModelResponse("", List.of(
                new ToolCall("call-1", "item-1", "missing", null)));

        assertThrows(AgentRunException.class,
                () -> new Runner(client, null, spans::add).runAgent(agent, "hello", null));

        Span toolSpan = spans.stream()
                .filter(span -> span.name().equals("tool.call"))
                .findFirst()
                .orElseThrow();
        assertEquals("error", toolSpan.attributes().get("status"));
        assertEquals("tool_resolution", toolSpan.attributes().get("error.stage"));
        assertEquals("missing", toolSpan.attributes().get("tool.name"));
    }

    /**
     * 验证达到最大轮次时会记录未执行的工具调用。
     */
    @Test
    void exportsSkippedToolSpanAtTurnLimit() {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger modelCalls = new AtomicInteger();
        List<Span> spans = new ArrayList<>();
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test").build();
        ModelClient client = request -> modelCalls.getAndIncrement() == 0
                ? new ModelResponse("", List.of(
                new ToolCall("call-1", "item-1", "unused", mapper.createObjectNode())))
                : ModelResponse.text("done");

        new Runner(client, 1, null, spans::add).runAgent(agent, "hello", null);

        Span skippedSpan = spans.stream()
                .filter(span -> "skipped".equals(span.attributes().get("status")))
                .findFirst()
                .orElseThrow();
        assertEquals("tool.call", skippedSpan.name());
        assertEquals("max_turns_exceeded", skippedSpan.attributes().get("skip.reason"));
        assertEquals("unused", skippedSpan.attributes().get("tool.name"));
    }

    /**
     * 验证运行器会按执行顺序导出完整的模型与工具生命周期事件。
     */
    @Test
    void exportsOrderedTraceEvents() {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger modelCalls = new AtomicInteger();
        List<Span> spans = new ArrayList<>();
        List<TraceEvent> events = new ArrayList<>();
        TraceExporter exporter = new TraceExporter() {
            @Override
            public void export(Span span) {
                spans.add(span);
            }

            @Override
            public void exportEvent(TraceEvent event) {
                events.add(event);
            }
        };
        Tool tool = new Tool() {
            public String name() {
                return "lookup";
            }

            public String description() {
                return "lookup";
            }

            public JsonNode inputSchema() {
                return mapper.createObjectNode();
            }

            public ToolResult call(JsonNode arguments, RunContext context) {
                return ToolResult.success(mapper.getNodeFactory().textNode("result"));
            }
        };
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test")
                .tools(List.of(tool)).build();
        ModelClient client = request -> modelCalls.getAndIncrement() == 0
                ? new ModelResponse("", List.of(
                new ToolCall("call-1", "item-1", "lookup", mapper.createObjectNode())))
                : ModelResponse.text("done");

        new Runner(client, null, exporter).runAgent(agent, "hello", null);

        assertEquals(List.of(
                        TraceEventType.RUN_STARTED,
                        TraceEventType.MODEL_REQUESTED,
                        TraceEventType.MODEL_COMPLETED,
                        TraceEventType.TOOL_REQUESTED,
                        TraceEventType.TOOL_COMPLETED,
                        TraceEventType.MODEL_REQUESTED,
                        TraceEventType.MODEL_COMPLETED,
                        TraceEventType.RUN_COMPLETED),
                events.stream().map(TraceEvent::type).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
                events.stream().map(TraceEvent::sequence).toList());
        assertEquals(1L, events.stream().map(TraceEvent::traceId).distinct().count());

        TraceEvent toolCompleted = events.get(4);
        assertEquals(1, toolCompleted.turn());
        assertEquals("call-1", toolCompleted.attributes().get("tool.call_id"));
        assertEquals(mapper.getNodeFactory().textNode("result"),
                toolCompleted.attributes().get("tool.output"));
        assertFalse(spans.isEmpty());
    }

    /**
     * 验证追踪导出失败不会中断智能体主流程。
     */
    @Test
    void ignoresTraceExporterFailure() {
        Agent agent = Agent.builder().name("Assistant").instructions("help").model("test").build();

        RunResult result = new Runner(request -> ModelResponse.text("ok"), null,
                span -> {
                    throw new IllegalStateException("追踪服务不可用");
                }).runAgent(agent, "hello", null);

        assertEquals("ok", result.finalOutput());
    }
}
