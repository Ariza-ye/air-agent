package com.ariza.agent.core.tracing;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 表示一次可按顺序重放的 Agent 轨迹事件。
 *
 * @param traceId    所属运行的追踪标识
 * @param sequence   运行内从 1 开始严格递增的事件序号
 * @param occurredAt 事件发生时间
 * @param type       事件类型
 * @param turn       所属模型调用轮次；运行级事件使用 0
 * @param attributes 事件附加数据
 * @author ariza
 */
public record TraceEvent(String traceId,
                         long sequence,
                         Instant occurredAt,
                         TraceEventType type,
                         int turn,
                         Map<String, Object> attributes) {

    public TraceEvent {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(type, "type");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (turn < 0) {
            throw new IllegalArgumentException("turn must not be negative");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
