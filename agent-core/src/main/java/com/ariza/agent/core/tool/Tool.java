package com.ariza.agent.core.tool;

import com.ariza.agent.core.RunContext;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author ariza
 */
public interface Tool {
    /**
     * 获取供模型识别和调用的工具名称。
     *
     * @return 工具名称
     */
    String name();

    /**
     * 获取工具用途说明。
     *
     * @return 工具说明
     */
    String description();

    /**
     * 获取描述工具输入参数的 JSON Schema。
     *
     * @return 工具输入参数结构
     */
    JsonNode inputSchema();

    /**
     * 使用指定参数和运行上下文调用工具。
     *
     * @param arguments 模型提供的工具调用参数
     * @param context   当前智能体运行上下文
     * @return 工具执行结果
     */
    ToolResult call(JsonNode arguments, RunContext context);
}
