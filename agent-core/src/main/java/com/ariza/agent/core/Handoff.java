package com.ariza.agent.core;

import java.util.Objects;

/**
 * @author ariza
 */
public record Handoff(String name, String description, Agent targetAgent) {
    /**
     * 创建一项任务移交配置。
     *
     * @param name        任务移交名称
     * @param description 任务移交的用途说明
     * @param targetAgent 接收任务的目标智能体
     * @throws NullPointerException 当名称或目标智能体为 {@code null} 时抛出
     */
    public Handoff {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(targetAgent, "targetAgent");
    }
}
