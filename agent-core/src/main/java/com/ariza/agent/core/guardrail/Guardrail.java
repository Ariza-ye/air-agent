package com.ariza.agent.core.guardrail;

import com.ariza.agent.core.RunContext;

/**
 * 数据安全围栏
 *
 * @author ariza
 */
@FunctionalInterface
public interface Guardrail {
    /**
     * 校验指定值是否满足护栏规则。
     *
     * @param value   需要校验的文本值
     * @param context 当前智能体运行上下文
     * @return 护栏校验结果及后续动作
     */
    GuardrailResult validate(String value, RunContext context);
}
