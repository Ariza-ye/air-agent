package com.ariza.agent.spring.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话存储配置。
 *
 * @author ariza
 */
@ConfigurationProperties("agents.session")
public class SessionProperties {

    private Type type = Type.MEMORY;

    /**
     * 获取当前会话存储类型。
     *
     * @return 会话存储类型
     */
    public Type getType() {
        return type;
    }

    /**
     * 设置会话存储类型。
     *
     * @param type 会话存储类型
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * 支持的会话存储类型。
     *
     * @author ariza
     */
    public enum Type {
        MEMORY,
        PG,
        MYSQL
    }
}
