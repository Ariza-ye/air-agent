package com.ariza.agent.core.tracing;

/**
 * Agent 运行过程中可观察的轨迹事件类型。
 *
 * @author ariza
 */
public enum TraceEventType {
    /**
     * Agent 开始处理一次用户请求。
     */
    RUN_STARTED,

    /**
     * Agent 成功完成一次用户请求。
     */
    RUN_COMPLETED,

    /**
     * Agent 处理用户请求时发生不可恢复的异常。
     */
    RUN_FAILED,

    /**
     * Runner 已组装模型请求，即将调用模型客户端。
     */
    MODEL_REQUESTED,

    /**
     * 模型客户端成功返回响应。
     */
    MODEL_COMPLETED,

    /**
     * 模型客户端调用失败。
     */
    MODEL_FAILED,

    /**
     * 模型请求执行一个工具，尚未产生执行结果。
     */
    TOOL_REQUESTED,

    /**
     * 工具成功完成执行并返回结果。
     */
    TOOL_COMPLETED,

    /**
     * 工具解析或执行失败。
     */
    TOOL_FAILED,

    /**
     * 工具调用因轮次限制等原因未执行。
     */
    TOOL_SKIPPED
}
