package com.ariza.agent.core.guardrail;

/**
 * 定义护栏完成校验后要求执行的处理动作。
 *
 * @author ariza
 */
public enum GuardrailAction {
    /**
     * 允许当前内容继续进入后续处理流程。
     */
    ALLOW,

    /**
     * 阻止当前内容继续处理并终止对应流程。
     */
    BLOCK,

    /**
     * 要求重写当前内容后再继续处理。
     */
    REWRITE,

    /**
     * 要求重新执行当前处理步骤。
     */
    RETRY,

    /**
     * 暂停自动处理并等待人工审批。
     */
    HUMAN_APPROVAL_REQUIRED
}
