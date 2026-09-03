package com.ariza.agent.spring;

import com.ariza.agent.core.Runner;
import com.ariza.agent.core.model.ModelClient;
import com.ariza.agent.core.session.SessionStore;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tracing.TraceExporter;
import com.ariza.agent.openai.OpenAIModelClient;
import com.ariza.agent.tool.reflect.AgentTool;
import com.ariza.agent.tool.reflect.ReflectionToolFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ariza
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentsProperties.class)
public class AgentsAutoConfiguration {

    /**
     * 根据 Spring Boot 配置创建默认模型客户端。
     *
     * @param properties OpenAI 连接配置
     * @return 使用配置中 API 密钥创建的模型客户端
     */
    @Bean
    @Conditional(OpenAiApiKeyCondition.class)
    @ConditionalOnMissingBean(ModelClient.class)
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
     * @param modelClient           Spring 容器中的模型客户端
     * @param sessionStoreProvider  Spring 容器中的可选会话存储
     * @param traceExporterProvider Spring 容器中的可选追踪导出器
     * @return 使用指定模型客户端创建的运行器
     */
    @Bean
    @ConditionalOnBean(ModelClient.class)
    @ConditionalOnMissingBean(Runner.class)
    Runner runner(ModelClient modelClient,
                  ObjectProvider<SessionStore> sessionStoreProvider,
                  ObjectProvider<TraceExporter> traceExporterProvider) {
        Runner.setDefaultModelClient(modelClient);
        SessionStore sessionStore = sessionStoreProvider.getIfAvailable();
        TraceExporter traceExporter = traceExporterProvider.getIfAvailable();
        return new Runner(modelClient, sessionStore, traceExporter);
    }

    /**
     * 创建默认反射工具工厂，允许应用按需提供自定义实现覆盖。
     *
     * @return 使用默认序列化配置的反射工具工厂
     */
    @Bean
    @ConditionalOnMissingBean(ReflectionToolFactory.class)
    ReflectionToolFactory reflectionToolFactory() {
        return new ReflectionToolFactory();
    }

    /**
     * 扫描 Spring 容器中声明了智能体工具方法的 Bean，并生成工具列表。
     *
     * @param beanFactory Spring Bean 工厂
     * @param toolFactory 反射工具工厂
     * @return 容器中全部反射工具
     */
    @Bean
    @ConditionalOnMissingBean(AgentTools.class)
    AgentTools reflectionTools(ConfigurableListableBeanFactory beanFactory,
                               ReflectionToolFactory toolFactory) {
        List<Tool> tools = new ArrayList<>();
        Set<String> toolNames = new HashSet<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !hasAgentToolMethod(beanType)) {
                continue;
            }

            Object bean = beanFactory.getBean(beanName);
            for (Tool tool : toolFactory.create(bean)) {
                if (!toolNames.add(tool.name())) {
                    throw new IllegalArgumentException("工具名称重复: " + tool.name());
                }
                tools.add(tool);
            }
        }
        return new AgentTools(tools);
    }

    /**
     * 判断指定类型或其父类是否声明了智能体工具方法。
     *
     * @param beanType Spring Bean 类型
     * @return 存在智能体工具方法时返回 {@code true}
     */
    private boolean hasAgentToolMethod(Class<?> beanType) {
        for (Class<?> type = beanType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isBridge() && !method.isSynthetic()
                        && method.isAnnotationPresent(AgentTool.class)) {
                    return true;
                }
            }
        }
        return false;
    }


}
