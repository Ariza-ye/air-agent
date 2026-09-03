package com.ariza.agent.core;

import com.ariza.agent.core.model.ModelClient;
import com.ariza.agent.core.model.ModelRequest;
import com.ariza.agent.core.model.ModelResponse;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tracing.TraceEventType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 执行模型请求并记录模型调用 Span 和生命周期事件。
 *
 * @author ariza
 */
final class ModelCallExecutor {
    private final ModelClient modelClient;
    private final RunTracer tracer;

    ModelCallExecutor(ModelClient modelClient, RunTracer tracer) {
        this.modelClient = modelClient;
        this.tracer = tracer;
    }

    /**
     * 调用模型并记录本轮请求及响应。
     */
    ModelResponse call(ModelRequest request, int turn) {
        if (!tracer.enabled()) {
            return modelClient.call(request);
        }

        String spanId = tracer.newSpanId();
        Instant startTime = Instant.now();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("model", request.model());
        attributes.put("turn", turn);
        attributes.put("model.input", request.input());
        attributes.put("model.tool_names",
                request.tools().stream().map(Tool::name).toList());
        attributes.put("model.has_continuation", request.continuation() != null);
        tracer.event(TraceEventType.MODEL_REQUESTED, turn, attributes);
        try {
            ModelResponse response = modelClient.call(request);
            attributes.put("status", "ok");
            attributes.put("tool_calls", response.toolCalls().size());
            attributes.put("model.status", response.status().name());
            attributes.put("model.output", response.text());
            tracer.putIfNotNull(attributes, "model.incomplete_reason", response.incompleteReason());
            tracer.addUsageAttributes(attributes, response.usage());
            attributes.put("tool.calls", response.toolCalls());
            tracer.event(TraceEventType.MODEL_COMPLETED, turn, attributes);
            return response;
        } catch (RuntimeException exception) {
            tracer.recordError(attributes, exception);
            tracer.event(TraceEventType.MODEL_FAILED, turn, attributes);
            throw exception;
        } finally {
            tracer.span(spanId, "model.call", startTime, Instant.now(), attributes);
        }
    }
}
