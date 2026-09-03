package com.ariza.agent.core.model;

/**
 * @author ariza
 */
@FunctionalInterface
public interface ModelClient {
    /**
     * 向模型发送请求并取得响应。
     *
     * @param request 模型请求参数
     * @return 模型生成的响应
     */
    ModelResponse call(ModelRequest request);
}
