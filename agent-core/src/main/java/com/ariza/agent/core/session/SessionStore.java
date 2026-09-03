package com.ariza.agent.core.session;

import java.util.List;

/**
 * @author ariza
 */
public interface SessionStore {

    /**
     * 加载指定会话中的全部消息。
     *
     * @param sessionId 会话唯一标识
     * @return 按存储顺序排列的消息列表
     */
    List<MessageItem> load(String sessionId);

    /**
     * 是否存在会话
     *
     * @param sessionId
     * @return
     */
    Boolean exists(String sessionId);

    /**
     * 将消息追加到指定会话。
     *
     * @param sessionId 会话唯一标识
     * @param items     需要追加的消息列表
     */
    void append(String sessionId, List<MessageItem> items);

    /**
     * 清除指定会话保存的全部消息。
     *
     * @param sessionId 会话唯一标识
     */
    void clear(String sessionId);
}
