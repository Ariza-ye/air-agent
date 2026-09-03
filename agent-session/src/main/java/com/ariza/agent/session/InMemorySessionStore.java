package com.ariza.agent.session;

import com.ariza.agent.core.session.MessageItem;
import com.ariza.agent.core.session.SessionStore;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存回话存储
 *
 * @author ariza
 */
public final class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MessageItem>> sessions = new ConcurrentHashMap<>();

    /**
     * 加载指定会话的消息快照。
     *
     * @param sessionId 会话唯一标识
     * @return 当前消息的不可变副本；会话不存在时返回空列表
     */
    @Override
    public List<MessageItem> load(String sessionId) {
        return List.copyOf(sessions.getOrDefault(sessionId, new CopyOnWriteArrayList<>()));
    }

    /**
     * 查询指定会话是否已保存消息。
     *
     * @param sessionId 会话唯一标识
     * @return 会话存在时返回 {@code true}
     */
    @Override
    public Boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 以线程安全方式向指定会话追加消息。
     *
     * @param sessionId 会话唯一标识
     * @param items     需要追加的消息列表
     */
    @Override
    public void append(String sessionId, List<MessageItem> items) {
        sessions.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<>()).addAll(items);
    }

    /**
     * 删除指定会话及其中保存的全部消息。
     *
     * @param sessionId 会话唯一标识
     */
    @Override
    public void clear(String sessionId) {
        sessions.remove(sessionId);
    }
}
