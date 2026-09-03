package com.ariza.agent.core;

import com.ariza.agent.core.model.ToolCall;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tool.ToolResult;
import com.ariza.agent.core.tracing.TraceEventType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 解析并执行工具调用，同时记录工具生命周期轨迹。
 *
 * @author ariza
 */
final class ToolCallExecutor {
    private final RunTracer tracer;

    ToolCallExecutor(RunTracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 记录模型返回的全部工具请求。
     */
    void recordRequested(List<ToolCall> toolCalls, int turn) {
        for (int toolIndex = 0; toolIndex < toolCalls.size(); toolIndex++) {
            tracer.event(TraceEventType.TOOL_REQUESTED, turn,
                    toolAttributes(toolCalls.get(toolIndex), turn, toolIndex));
        }
    }

    /**
     * 执行一个工具调用并记录执行结果。
     */
    ToolResult call(List<Tool> tools, ToolCall toolCall, RunContext context,
                    int turn, int toolIndex) {
        if (!tracer.enabled()) {
            return findTool(tools, toolCall.name()).call(toolCall.arguments(), context);
        }

        Map<String, Object> attributes = toolAttributes(toolCall, turn, toolIndex);
        String spanId = tracer.newSpanId();
        Instant startTime = Instant.now();
        long startNanos = System.nanoTime();
        try {
            Tool tool;
            try {
                tool = findTool(tools, toolCall.name());
            } catch (AgentRunException exception) {
                attributes.put("error.stage", "tool_resolution");
                throw exception;
            }
            ToolResult result = tool.call(toolCall.arguments(), context);
            attributes.put("status", result.success() ? "ok" : "error");
            tracer.putIfNotNull(attributes, "tool.output", result.output());
            if (!result.success() && result.error() != null) {
                attributes.put("error.stage", "tool_execution");
                attributes.put("error.message", result.error());
                attributes.put("tool.error", result.error());
            }
            attributes.put("duration.millis", elapsedMillis(startNanos));
            tracer.event(result.success() ? TraceEventType.TOOL_COMPLETED : TraceEventType.TOOL_FAILED,
                    turn, attributes);
            return result;
        } catch (RuntimeException exception) {
            attributes.putIfAbsent("error.stage", "tool_execution");
            tracer.recordError(attributes, exception);
            attributes.put("duration.millis", elapsedMillis(startNanos));
            tracer.event(TraceEventType.TOOL_FAILED, turn, attributes);
            throw exception;
        } finally {
            tracer.span(spanId, "tool.call", startTime, Instant.now(), attributes);
        }
    }

    /**
     * 记录因轮次限制而未执行的工具调用。
     */
    void recordSkipped(List<ToolCall> toolCalls, int turn) {
        if (!tracer.enabled()) {
            return;
        }
        for (int toolIndex = 0; toolIndex < toolCalls.size(); toolIndex++) {
            Instant occurredAt = Instant.now();
            Map<String, Object> attributes = toolAttributes(toolCalls.get(toolIndex), turn, toolIndex);
            attributes.put("status", "skipped");
            attributes.put("skip.reason", "max_turns_exceeded");
            tracer.event(TraceEventType.TOOL_SKIPPED, turn, attributes);
            tracer.span(tracer.newSpanId(), "tool.call", occurredAt, occurredAt, attributes);
        }
    }

    /**
     * 创建工具调用的公共追踪属性。
     */
    private Map<String, Object> toolAttributes(ToolCall toolCall, int turn, int toolIndex) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        tracer.putIfNotNull(attributes, "tool.name", toolCall.name());
        tracer.putIfNotNull(attributes, "tool.call_id", toolCall.callId());
        tracer.putIfNotNull(attributes, "tool.item_id", toolCall.itemId());
        tracer.putIfNotNull(attributes, "tool.arguments", toolCall.arguments());
        attributes.put("turn", turn);
        attributes.put("tool.index", toolIndex);
        return attributes;
    }

    /**
     * 查找指定名称的工具。
     */
    private Tool findTool(List<Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AgentRunException("未找到工具: " + name));
    }

    /**
     * 计算已经消耗的毫秒数。
     */
    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
