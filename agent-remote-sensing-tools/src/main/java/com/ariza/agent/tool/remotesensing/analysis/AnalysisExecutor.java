package com.ariza.agent.tool.remotesensing.analysis;

import com.ariza.agent.core.RunContext;
import com.ariza.agent.core.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 遥感分析任务的实际执行入口。
 *
 * <p>工具层只负责向模型暴露稳定的名称、参数结构和参数校验；具体的 GDAL、云端处理服务或其他
 * 分析引擎由应用通过此接口接入。</p>
 *
 * @author ariza
 */
@FunctionalInterface
public interface AnalysisExecutor {

    /**
     * 执行一个遥感分析任务。
     *
     * @param operation 工具操作名称
     * @param arguments 已通过工具校验的原始参数
     * @param context   当前运行上下文
     * @return 执行结果
     */
    ToolResult execute(String operation, JsonNode arguments, RunContext context);
}
