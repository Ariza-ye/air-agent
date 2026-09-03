package com.ariza.agent.example;

import com.ariza.agent.core.Runner;
import com.ariza.agent.core.model.ModelClient;
import com.ariza.agent.core.session.SessionStore;
import com.ariza.agent.core.tracing.TraceExporter;
import com.ariza.agent.openai.OpenAIModelClient;
import com.ariza.agent.spring.AgentsProperties;
import com.ariza.agent.tool.reflect.ReflectionToolFactory;
import com.ariza.agent.tracing.LogTraceExporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 *
 * @author ariza
 * @since 2026-08-06 16:06:04
 */
@Configuration
public class AgentsConfiguration {

    /**
     * 创建 OpenAI 客户端
     *
     * @param properties OpenAI 连接配置
     * @return 使用配置中 API 密钥创建的模型客户端
     */
    @Bean
    ModelClient modelClient(AgentsProperties properties) {
        String endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return new OpenAIModelClient(properties.getApiKey());
        }
        return new OpenAIModelClient(properties.getApiKey(), URI.create(endpoint));
    }

    /**
     * 创建默认智能体运行器，并注册其静态默认模型客户端。
     *
     * @param modelClient Spring 容器中的模型客户端
     * @return 使用指定模型客户端创建的运行器
     */
    @Bean
    Runner runner(ModelClient modelClient, SessionStore sessionStore, TraceExporter traceExporter) {
        Runner.setDefaultModelClient(modelClient);
        return new Runner(modelClient, sessionStore, traceExporter);
    }

    /**
     * 使用 Spring Boot 的 Jackson 配置创建反射工具工厂。
     *
     * @param objectMapper Spring 容器中的对象映射器
     * @return 反射工具工厂
     */
    @Bean
    ReflectionToolFactory reflectionToolFactory(ObjectMapper objectMapper) {
        return new ReflectionToolFactory(objectMapper);
    }

    /**
     * 日志追踪器
     *
     * @return 日志追踪导出器
     */
    @Bean
    TraceExporter traceExporter() {
        return new LogTraceExporter();
    }

}
