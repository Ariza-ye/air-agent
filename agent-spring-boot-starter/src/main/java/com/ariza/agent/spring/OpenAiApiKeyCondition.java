package com.ariza.agent.spring;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * @author ariza
 */
final class OpenAiApiKeyCondition extends SpringBootCondition {

    private static final String PROPERTY_NAME = "agents.ai.api-key";

    /**
     * 判断是否配置了可用于创建默认 OpenAI 客户端的 API Key。
     *
     * @param context  条件上下文
     * @param metadata 被检查的注解元数据
     * @return API Key 包含有效文本时匹配
     */
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String apiKey = context.getEnvironment().getProperty(PROPERTY_NAME);
        ConditionMessage.Builder message = ConditionMessage.forCondition("OpenAI API Key");
        if (StringUtils.hasText(apiKey)) {
            return ConditionOutcome.match(message.found("property").items(PROPERTY_NAME));
        }
        return ConditionOutcome.noMatch(message.didNotFind("non-blank property").items(PROPERTY_NAME));
    }
}
