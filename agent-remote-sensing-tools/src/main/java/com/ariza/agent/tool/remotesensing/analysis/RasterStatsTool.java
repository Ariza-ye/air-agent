package com.ariza.agent.tool.remotesensing.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 计算栅格影像的描述性统计量。
 *
 * @author ariza
 */
public final class RasterStatsTool extends AbstractAnalysisTool {
    /**
     * 模型调用时使用的工具名称。
     */
    public static final String NAME = "rasterStats";

    /**
     * 创建栅格统计工具。
     *
     * @param executor 实际执行栅格统计任务的执行器
     */
    public RasterStatsTool(AnalysisExecutor executor) {
        super(NAME, "计算栅格影像指定波段的最小值、最大值、均值等统计量", schema(), executor);
    }

    /**
     * 构建栅格统计工具的输入参数 Schema。
     *
     * @return 输入参数 Schema
     */
    private static JsonNode schema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        properties.set("rasterUri", stringProperty("栅格影像 URI 或分析引擎可访问的文件路径"));
        properties.set("bands", bandArrayProperty());
        properties.set("statistics", stringArrayProperty(
                "需要计算的统计量；不传时计算分析引擎的默认统计量",
                "count", "min", "max", "mean", "median", "sum", "stddev"));
        properties.set("noData", numberProperty("可选的无效像元值"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) schema.get("required")).add("rasterUri");
        return schema;
    }

    @Override
    protected void validate(JsonNode arguments) {
        requireNonBlankString(arguments, "rasterUri");
        validateOptionalBands(arguments);
        validateOptionalStringArray(arguments, "statistics");
        JsonNode noData = arguments.get("noData");
        if (noData != null && !noData.isNumber()) {
            throw new IllegalArgumentException("noData 必须是数值");
        }
    }
}
