package com.ariza.agent.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author ariza
 */
@ConfigurationProperties("agents.ai")
public class AgentsProperties {
    private String apiKey;
    private String defaultModel = "gpt-4.1-mini";
    private String endpoint;

    /**
     * 获取调用模型 API 使用的密钥。
     *
     * @return API 密钥
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置调用模型 API 使用的密钥。
     *
     * @param apiKey API 密钥
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 获取未显式指定模型时使用的默认模型名称。
     *
     * @return 默认模型名称
     */
    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * 设置未显式指定模型时使用的默认模型名称。
     *
     * @param defaultModel 默认模型名称
     */
    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    /**
     * 获取模型客户端使用的 API 地址；未配置时使用客户端内置默认地址。
     *
     * @return API 地址
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置模型客户端使用的 API 地址。
     *
     * @param endpoint API 地址
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}
