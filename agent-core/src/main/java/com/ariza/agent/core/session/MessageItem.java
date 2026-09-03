package com.ariza.agent.core.session;

import java.time.Instant;

/**
 * @author ariza
 */
public record MessageItem(MessageRole role, String content, Instant createdAt) {
    /**
     * 使用当前时间创建一条会话消息。
     *
     * @param role    消息角色
     * @param content 消息内容
     */
    public MessageItem(MessageRole role, String content) {
        this(role, content, Instant.now());
    }
}
