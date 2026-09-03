package com.ariza.agent.core;

import com.ariza.agent.core.model.*;
import com.ariza.agent.core.session.MessageItem;
import com.ariza.agent.core.session.MessageRole;
import com.ariza.agent.core.session.SessionStore;
import com.ariza.agent.core.tool.ToolResult;
import com.ariza.agent.core.tracing.TraceExporter;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 编排一次完整的 Agent 模型与工具调用循环。
 *
 * @author ariza
 */
final class AgentRunEngine {
    private static final String FINAL_SUMMARY_INSTRUCTION =
            "请停止调用工具，仅基于当前对话和已经获得的工具结果给出最终答复。"
                    + "信息不足时请明确说明无法确认的部分，不要提及工具调用轮次或内部限制。";
    private static final String SKIPPED_TOOL_OUTPUT = "该工具调用未执行，请基于已有信息完成回答。";
    private static final String EMPTY_SUMMARY_FALLBACK = "抱歉，当前信息不足，暂时无法给出可靠结论。";

    private final ModelClient modelClient;
    private final int maxTurns;
    private final SessionStore sessionStore;
    private final TraceExporter traceExporter;
    private final Logger logger = Logger.getLogger(Runner.class.getName());

    AgentRunEngine(ModelClient modelClient, int maxTurns,
                   SessionStore sessionStore, TraceExporter traceExporter) {
        this.modelClient = modelClient;
        this.maxTurns = maxTurns;
        this.sessionStore = sessionStore;
        this.traceExporter = traceExporter;
    }

    /**
     * 执行一次 Agent 请求并维护根追踪生命周期。
     */
    RunResult run(Agent agent, String input, String sessionId, RunContext context) {
        if (sessionStore != null) {
            Objects.requireNonNull(sessionId, "sessionId");
        }

        RunTracer tracer = new RunTracer(traceExporter, context, agent, input, sessionId);
        try {
            RunResult result = runLoop(agent, input, sessionId, context, tracer);
            tracer.complete(result);
            return result;
        } catch (RuntimeException exception) {
            tracer.fail(exception);
            throw exception;
        } finally {
            tracer.close();
        }
    }

    /**
     * 执行模型与工具循环直至获得最终文本。
     */
    private RunResult runLoop(Agent agent, String input, String sessionId,
                              RunContext context, RunTracer tracer) {
        ModelCallExecutor modelExecutor = new ModelCallExecutor(modelClient, tracer);
        ToolCallExecutor toolExecutor = new ToolCallExecutor(tracer);
        List<ModelInputItem> modelInputs = restoreModelInputs(sessionId);
        modelInputs.add(new UserInput(input));

        ModelResponse response = callModel(modelExecutor, toolExecutor,
                new ModelRequest(agent.model(), agent.instructions(), modelInputs, agent.tools(), null), 1);
        ModelUsage usage = response.usage();
        int turns = 1;
        String outputBeforeSummary = "";
        boolean summarizedAtLimit = false;
        Map<String, List<Long>> toolTime = new LinkedHashMap<>();

        while (response.hasToolCalls()) {
            if (turns >= maxTurns) {
                outputBeforeSummary = response.text();
                toolExecutor.recordSkipped(response.toolCalls(), turns);
                response = summarizeAtTurnLimit(agent, response, modelExecutor, toolExecutor, turns + 1);
                usage = usage.add(response.usage());
                turns++;
                summarizedAtLimit = true;
                break;
            }

            List<ModelInputItem> toolOutputs = executeTools(
                    agent, response.toolCalls(), context, toolExecutor, turns, toolTime);
            response = callModel(modelExecutor, toolExecutor, new ModelRequest(
                    agent.model(),
                    agent.instructions(),
                    toolOutputs,
                    agent.tools(),
                    response.continuation()), turns + 1);
            usage = usage.add(response.usage());
            turns++;
        }

        String finalOutput = response.text();
        if (summarizedAtLimit && finalOutput.isBlank()) {
            finalOutput = outputBeforeSummary.isBlank() ? EMPTY_SUMMARY_FALLBACK : outputBeforeSummary;
        }
        saveSession(sessionId, input, finalOutput);
        logToolDuration(sessionId, toolTime);
        return new RunResult(finalOutput, context.runId(), sessionId, turns, usage);
    }

    /**
     * 恢复会话中可发送给模型的历史消息。
     */
    private List<ModelInputItem> restoreModelInputs(String sessionId) {
        List<MessageItem> history = sessionStore == null
                ? List.of()
                : sessionStore.load(sessionId);
        List<ModelInputItem> modelInputs = new ArrayList<>();
        for (MessageItem item : history) {
            switch (item.role()) {
                case USER -> modelInputs.add(new UserInput(item.content()));
                case ASSISTANT -> modelInputs.add(new AIInput(item.content()));
                case SYSTEM -> {
                    // 系统指令统一使用 agent.instructions()，避免重复注入
                }
                case TOOL -> throw new AgentRunException("暂不支持恢复工具消息");
            }
        }
        return modelInputs;
    }

    /**
     * 调用模型并记录响应中的工具请求事件。
     */
    private ModelResponse callModel(ModelCallExecutor modelExecutor,
                                    ToolCallExecutor toolExecutor,
                                    ModelRequest request, int turn) {
        ModelResponse response = modelExecutor.call(request, turn);
        toolExecutor.recordRequested(response.toolCalls(), turn);
        return response;
    }

    /**
     * 按模型返回顺序执行本轮全部工具调用。
     */
    private List<ModelInputItem> executeTools(Agent agent, List<ToolCall> toolCalls,
                                              RunContext context, ToolCallExecutor toolExecutor,
                                              int turn, Map<String, List<Long>> toolTime) {
        List<ModelInputItem> toolOutputs = new ArrayList<>(toolCalls.size());
        for (int toolIndex = 0; toolIndex < toolCalls.size(); toolIndex++) {
            ToolCall toolCall = toolCalls.get(toolIndex);
            long startNanos = System.nanoTime();
            ToolResult result;
            try {
                result = toolExecutor.call(agent.tools(), toolCall, context, turn, toolIndex);
            } catch (AgentRunException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                String message = exception.getMessage();
                result = ToolResult.failure(message == null || message.isBlank()
                        ? exception.getClass().getSimpleName()
                        : message);
            } finally {
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                toolTime.computeIfAbsent(toolCall.name(), ignored -> new ArrayList<>()).add(elapsedMillis);
            }
            String output = result.success()
                    ? String.valueOf(result.output())
                    : "工具执行失败: " + String.valueOf(result.error());
            toolOutputs.add(new ToolOutput(toolCall.callId(), output));
        }
        return toolOutputs;
    }

    /**
     * 达到轮次上限后禁用工具并请求模型生成最终答复。
     */
    private ModelResponse summarizeAtTurnLimit(Agent agent, ModelResponse response,
                                               ModelCallExecutor modelExecutor,
                                               ToolCallExecutor toolExecutor, int turn) {
        List<ModelInputItem> summaryInputs = new ArrayList<>(response.toolCalls().size() + 1);
        for (ToolCall toolCall : response.toolCalls()) {
            summaryInputs.add(new ToolOutput(toolCall.callId(), SKIPPED_TOOL_OUTPUT));
        }
        summaryInputs.add(new UserInput(FINAL_SUMMARY_INSTRUCTION));
        return callModel(modelExecutor, toolExecutor, new ModelRequest(
                agent.model(),
                agent.instructions(),
                summaryInputs,
                List.of(),
                response.continuation()), turn);
    }

    /**
     * 保存本次运行的用户输入和最终回复。
     */
    private void saveSession(String sessionId, String input, String finalOutput) {
        if (sessionStore == null) {
            return;
        }
        sessionStore.append(sessionId, List.of(
                new MessageItem(MessageRole.USER, input),
                new MessageItem(MessageRole.ASSISTANT, finalOutput)
        ));
    }

    /**
     * 输出同名工具每次调用的耗时。
     */
    private void logToolDuration(String sessionId, Map<String, List<Long>> toolTime) {
        if (sessionId != null && !sessionId.isEmpty() && !toolTime.isEmpty()) {
            logger.log(Level.INFO, "sessionId:" + sessionId + ",工具调用耗时（毫秒）: " + toolTime);
        }
    }

    /**
     * 获取最大模型调用轮次。
     */
    int maxTurns() {
        return maxTurns;
    }
}
