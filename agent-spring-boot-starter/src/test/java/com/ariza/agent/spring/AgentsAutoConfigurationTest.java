package com.ariza.agent.spring;

import com.ariza.agent.core.Agent;
import com.ariza.agent.core.Runner;
import com.ariza.agent.core.model.ModelClient;
import com.ariza.agent.core.model.ModelResponse;
import com.ariza.agent.core.tracing.Span;
import com.ariza.agent.core.tracing.TraceExporter;
import com.ariza.agent.openai.OpenAIModelClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author ariza
 */
class AgentsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentsAutoConfiguration.class));

    /**
     * 验证未配置 API Key 时应用可以正常启动，且不会创建依赖模型的默认 Bean。
     */
    @Test
    void startsWithoutApiKey() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ModelClient.class);
            assertThat(context).doesNotHaveBean(Runner.class);
        });
    }

    /**
     * 验证 API Key 为空字符串时应用可以正常启动。
     */
    @Test
    void startsWithBlankApiKey() {
        contextRunner
                .withPropertyValues("agents.ai.api-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ModelClient.class);
                    assertThat(context).doesNotHaveBean(Runner.class);
                });
    }

    /**
     * 验证配置有效 API Key 时创建默认 OpenAI 客户端和运行器。
     */
    @Test
    void createsDefaultBeansWhenApiKeyIsConfigured() {
        contextRunner
                .withPropertyValues("agents.ai.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(ModelClient.class);
                    assertThat(context).hasSingleBean(OpenAIModelClient.class);
                    assertThat(context).hasSingleBean(Runner.class);
                });
    }

    /**
     * 验证未配置 API Key 但存在自定义模型客户端时仍创建默认运行器。
     */
    @Test
    void createsRunnerForCustomModelClientWithoutApiKey() {
        ModelClient customModelClient = request -> null;

        contextRunner
                .withBean(ModelClient.class, () -> customModelClient)
                .run(context -> {
                    assertThat(context).hasSingleBean(ModelClient.class);
                    assertThat(context.getBean(ModelClient.class)).isSameAs(customModelClient);
                    assertThat(context).hasSingleBean(Runner.class);
                });
    }

    /**
     * 验证 Starter 会把可选追踪导出器注入默认运行器。
     */
    @Test
    void configuresRunnerWithOptionalTraceExporter() {
        List<Span> spans = new ArrayList<>();
        ModelClient modelClient = request -> ModelResponse.text("ok");
        TraceExporter traceExporter = spans::add;

        contextRunner
                .withBean(ModelClient.class, () -> modelClient)
                .withBean(TraceExporter.class, () -> traceExporter)
                .run(context -> {
                    Agent agent = Agent.builder().name("Assistant").instructions("help").model("test").build();
                    context.getBean(Runner.class).runAgent(agent, "hello", null);

                    assertThat(spans).extracting(Span::name)
                            .containsExactly("model.call", "agent.run");
                });
    }
}
