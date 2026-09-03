package com.ariza.agent.core.guardrail;

/**
 * @author ariza
 */
public record GuardrailResult(boolean passed, String reason, GuardrailAction action) {
    /**
     * 创建一个允许继续处理的护栏结果。
     *
     * @return 表示校验通过且动作为允许的结果
     */
    public static GuardrailResult allow() {
        return new GuardrailResult(true, null, GuardrailAction.ALLOW);
    }

    /**
     * 阻止执行
     *
     * @return
     */
    public static GuardrailResult block() {
        return new GuardrailResult(true, null, GuardrailAction.BLOCK);
    }
}
