package com.ariza.agent.core.tracing;

import java.time.Instant;
import java.util.Map;

/**
 * 表示一次可导出的追踪跨度，记录操作的层级关系、执行时间及附加属性。
 *
 * @param traceId      所属追踪的唯一标识
 * @param spanId       当前跨度的唯一标识
 * @param parentSpanId 父跨度标识；根跨度可为空
 * @param name         跨度名称
 * @param startTime    开始时间
 * @param endTime      结束时间
 * @param attributes   与跨度关联的扩展属性
 * @author ariza
 */
public record Span(String traceId, String spanId, String parentSpanId, String name,
                   Instant startTime, Instant endTime, Map<String, Object> attributes) {

    public Span {
        attributes = Map.copyOf(attributes);
    }
}
