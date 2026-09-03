package com.ariza.agent.core;

/**
 * @author ariza
 */
public class AgentRunException extends RuntimeException {
    /**
     * 使用指定错误信息创建智能体运行异常。
     *
     * @param message 错误信息
     */
    public AgentRunException(String message) {
        super(message);
    }

    /**
     * 使用指定错误信息和原始异常创建智能体运行异常。
     *
     * @param message 错误信息
     * @param cause   导致运行失败的原始异常
     */
    public AgentRunException(String message, Throwable cause) {
        super(message, cause);
    }
}
