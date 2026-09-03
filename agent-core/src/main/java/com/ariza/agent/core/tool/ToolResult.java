package com.ariza.agent.core.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author ariza
 */
public record ToolResult(boolean success, JsonNode output, String error) {
    /**
     * 创建一个执行成功的工具结果。
     *
     * @param output 工具输出数据
     * @return 成功的工具结果
     */
    public static ToolResult success(JsonNode output) {
        return new ToolResult(true, output, null);
    }

    /**
     * 创建一个执行失败的工具结果。
     *
     * @param error 失败原因
     * @return 失败的工具结果
     */
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error);
    }
}
