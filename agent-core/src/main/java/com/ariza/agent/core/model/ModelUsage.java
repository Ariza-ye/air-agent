package com.ariza.agent.core.model;

/**
 * 一次或多次模型调用消耗的 token 用量。
 *
 * @param inputTokens       输入 token 数
 * @param outputTokens      输出 token 数
 * @param totalTokens       总 token 数
 * @param cachedInputTokens 命中缓存的输入 token 数
 * @param reasoningTokens   推理 token 数
 * @author ariza
 */
public record ModelUsage(long inputTokens,
                         long outputTokens,
                         long totalTokens,
                         long cachedInputTokens,
                         long reasoningTokens) {

    private static final ModelUsage ZERO = new ModelUsage(0, 0, 0, 0, 0);

    public ModelUsage {
        if (inputTokens < 0
                || outputTokens < 0
                || totalTokens < 0
                || cachedInputTokens < 0
                || reasoningTokens < 0) {
            throw new IllegalArgumentException("token usage must not be negative");
        }
    }

    /**
     * 返回不包含任何 token 消耗的用量对象。
     *
     * @return 全部字段为零的用量
     */
    public static ModelUsage zero() {
        return ZERO;
    }

    /**
     * 合并另一笔模型用量。
     *
     * @param other 要累加的用量
     * @return 累加后的新用量对象
     */
    public ModelUsage add(ModelUsage other) {
        if (other == null) {
            return this;
        }
        return new ModelUsage(
                Math.addExact(inputTokens, other.inputTokens),
                Math.addExact(outputTokens, other.outputTokens),
                Math.addExact(totalTokens, other.totalTokens),
                Math.addExact(cachedInputTokens, other.cachedInputTokens),
                Math.addExact(reasoningTokens, other.reasoningTokens));
    }
}
