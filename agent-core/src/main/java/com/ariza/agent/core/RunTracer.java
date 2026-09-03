package com.ariza.agent.core;

import com.ariza.agent.core.model.ModelUsage;
import com.ariza.agent.core.tracing.Span;
import com.ariza.agent.core.tracing.TraceEvent;
import com.ariza.agent.core.tracing.TraceEventType;
import com.ariza.agent.core.tracing.TraceExporter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 管理单次 Agent 运行的 Span、事件序号和安全导出。
 *
 * @author ariza
 */
final class RunTracer {
    private final TraceExporter traceExporter;
    private final String traceId;
    private final String rootSpanId;
    private final AtomicLong sequence = new AtomicLong();
    private final Instant startTime;
    private final Map<String, Object> runAttributes;
    private final Logger logger = Logger.getLogger(Runner.class.getName());

    RunTracer(TraceExporter traceExporter, RunContext context, Agent agent,
              String input, String sessionId) {
        this.traceExporter = traceExporter;
        this.traceId = context.runId();
        this.rootSpanId = traceExporter == null ? null : newSpanId();
        this.startTime = Instant.now();
        this.runAttributes = new LinkedHashMap<>();
        runAttributes.put("agent.name", agent.name());
        runAttributes.put("agent.model", agent.model());
        runAttributes.put("user.input", input);
        putIfNotNull(runAttributes, "session.id",
                sessionId == null || sessionId.isBlank() ? null : sessionId);
        event(TraceEventType.RUN_STARTED, 0, runAttributes);
    }

    /**
     * 判断当前运行是否启用了追踪。
     */
    boolean enabled() {
        return traceExporter != null;
    }

    /**
     * 创建新的 Span 标识。
     */
    String newSpanId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 记录运行成功结果。
     */
    void complete(RunResult result) {
        if (!enabled()) {
            return;
        }
        runAttributes.put("status", "ok");
        runAttributes.put("turns", result.turns());
        runAttributes.put("final.output", result.finalOutput());
        addUsageAttributes(runAttributes, result.usage());
        event(TraceEventType.RUN_COMPLETED, 0, runAttributes);
    }

    /**
     * 记录导致运行终止的异常。
     */
    void fail(RuntimeException exception) {
        if (!enabled()) {
            return;
        }
        recordError(runAttributes, exception);
        event(TraceEventType.RUN_FAILED, 0, runAttributes);
    }

    /**
     * 完成并导出根运行 Span。
     */
    void close() {
        if (!enabled()) {
            return;
        }
        exportSpan(new Span(traceId, rootSpanId, null, "agent.run",
                startTime, Instant.now(), runAttributes));
    }

    /**
     * 导出挂载到根运行 Span 的子 Span。
     */
    void span(String spanId, String name, Instant startTime,
              Instant endTime, Map<String, Object> attributes) {
        if (!enabled()) {
            return;
        }
        exportSpan(new Span(traceId, spanId, rootSpanId, name,
                startTime, endTime, attributes));
    }

    /**
     * 导出一条运行内有序事件。
     */
    void event(TraceEventType type, int turn, Map<String, Object> attributes) {
        if (!enabled()) {
            return;
        }
        try {
            traceExporter.exportEvent(new TraceEvent(
                    traceId,
                    sequence.incrementAndGet(),
                    Instant.now(),
                    type,
                    turn,
                    attributes));
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "导出 Agent 轨迹事件失败: " + type, exception);
        }
    }

    /**
     * 记录统一异常属性。
     */
    void recordError(Map<String, Object> attributes, RuntimeException exception) {
        attributes.put("status", "error");
        attributes.put("error.type", exception.getClass().getName());
        putIfNotNull(attributes, "error.message", exception.getMessage());
    }

    /**
     * 记录模型 Token 用量。
     */
    void addUsageAttributes(Map<String, Object> attributes, ModelUsage usage) {
        attributes.put("usage.input_tokens", usage.inputTokens());
        attributes.put("usage.output_tokens", usage.outputTokens());
        attributes.put("usage.total_tokens", usage.totalTokens());
        attributes.put("usage.cached_input_tokens", usage.cachedInputTokens());
        attributes.put("usage.reasoning_tokens", usage.reasoningTokens());
    }

    /**
     * 仅记录非空属性值。
     */
    void putIfNotNull(Map<String, Object> attributes, String name, Object value) {
        if (value != null) {
            attributes.put(name, value);
        }
    }

    /**
     * 安全导出 Span，避免追踪系统故障影响主流程。
     */
    private void exportSpan(Span span) {
        try {
            traceExporter.export(span);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "导出 Agent 追踪跨度失败: " + span.name(), exception);
        }
    }
}
