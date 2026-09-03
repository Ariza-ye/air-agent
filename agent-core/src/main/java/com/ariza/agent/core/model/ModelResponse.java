package com.ariza.agent.core.model;

import java.util.List;

/**
 * @author ariza
 */
public record ModelResponse(String text,
                            List<ToolCall> toolCalls,
                            ModelContinuation continuation,
                            ModelStatus status,
                            String incompleteReason,
                            ModelUsage usage) {
    /**
     * 创建模型响应，并规范化空文本及工具调用列表。
     *
     * @param text             模型返回的文本，传入 {@code null} 时转换为空字符串
     * @param toolCalls        模型请求执行的工具调用，传入 {@code null} 时使用空列表
     * @param continuation     模型后续请求所需的续传信息
     * @param status           模型响应状态，传入 {@code null} 时视为已完成
     * @param incompleteReason 响应未完成的原因
     * @param usage            本次模型调用的 token 用量
     */
    public ModelResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        status = status == null ? ModelStatus.COMPLETED : status;
        usage = usage == null ? ModelUsage.zero() : usage;
    }

    /**
     * 创建不包含用量信息的模型响应。
     */
    public ModelResponse(String text,
                         List<ToolCall> toolCalls,
                         ModelContinuation continuation,
                         ModelStatus status,
                         String incompleteReason) {
        this(text, toolCalls, continuation, status, incompleteReason, ModelUsage.zero());
    }

    /**
     * 创建一个已完成的模型响应。
     *
     * @param text      模型返回的文本
     * @param toolCalls 模型请求执行的工具调用
     */
    public ModelResponse(String text, List<ToolCall> toolCalls) {
        this(text, toolCalls, null, ModelStatus.COMPLETED, null, ModelUsage.zero());
    }

    /**
     * 创建一个仅包含文本、不包含工具调用的模型响应。
     *
     * @param text 模型返回的文本
     * @return 文本模型响应
     */
    public static ModelResponse text(String text) {
        return new ModelResponse(text, List.of());
    }

    /**
     * 判断模型响应是否包含工具调用。
     *
     * @return 包含至少一个工具调用时返回 {@code true}
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
