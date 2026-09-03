package com.ariza.agent.core;

import com.ariza.agent.core.model.ModelUsage;

import java.util.Objects;

/**
 * @author ariza
 */
public record RunResult(String finalOutput,
                        String runId,
                        String sessionId,
                        int turns,
                        ModelUsage usage) {
    /**
     * 创建智能体运行结果。
     *
     * @param finalOutput 智能体最终输出文本
     * @param runId       本次运行的唯一标识
     * @param turns       本次运行消耗的轮次
     * @param usage       本次运行所有模型调用累计的 token 用量
     * @throws NullPointerException 当最终输出或运行标识为 {@code null} 时抛出
     */
    public RunResult {
        Objects.requireNonNull(finalOutput, "finalOutput");
        Objects.requireNonNull(runId, "runId");
        usage = usage == null ? ModelUsage.zero() : usage;
    }

    /**
     * 创建不包含模型用量信息的运行结果。
     */
    public RunResult(String finalOutput, String runId, String sessionId, int turns) {
        this(finalOutput, runId, sessionId, turns, ModelUsage.zero());
    }

    /**
     * 在最终输出末尾追加文本，并返回新的运行结果。
     *
     * @param additionalOutput 需要追加的文本
     * @return 追加文本后的新运行结果
     * @throws NullPointerException 当追加文本为 {@code null} 时抛出
     */
    public RunResult appendFinalOutput(String additionalOutput) {
        Objects.requireNonNull(additionalOutput, "additionalOutput");
        return new RunResult(finalOutput + additionalOutput, runId, sessionId, turns, usage);
    }
}
