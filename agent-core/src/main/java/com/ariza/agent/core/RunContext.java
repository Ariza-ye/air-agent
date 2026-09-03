package com.ariza.agent.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ariza
 */
public final class RunContext {
    private final String runId = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 获取本次智能体运行的唯一标识。
     *
     * @return 运行唯一标识
     */
    public String runId() {
        return runId;
    }

    /**
     * 获取本次智能体运行的开始时间。
     *
     * @return 运行开始时间
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * 获取用于在运行期间共享数据的线程安全属性集合。
     *
     * @return 可变的运行属性映射
     */
    public Map<String, Object> attributes() {
        return attributes;
    }
}
