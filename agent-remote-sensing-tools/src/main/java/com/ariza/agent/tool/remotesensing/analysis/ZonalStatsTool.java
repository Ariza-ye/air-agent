package com.ariza.agent.tool.remotesensing.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 按矢量分区汇总栅格统计量。
 *
 * @author ariza
 */
public final class ZonalStatsTool extends AbstractAnalysisTool {
    /**
     * 模型调用时使用的工具名称。
     */
    public static final String NAME = "zonalStats";

    /**
     * 创建分区统计工具。
     *
     * @param executor 实际执行分区统计任务的执行器
     */
    public ZonalStatsTool(AnalysisExecutor executor) {
        super(NAME, "按矢量区域计算栅格影像的分区统计量", schema(), executor);
    }

    /**
     * 构建分区统计工具的输入参数 Schema。
     *
     * @return 输入参数 Schema
     */
    private static JsonNode schema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        properties.set("rasterUri", stringProperty("输入栅格影像 URI 或文件路径"));
        properties.set("zonesUri", stringProperty("分区矢量数据 URI 或文件路径"));
        properties.set("zoneField", stringProperty("用于标识分区的矢量属性字段"));
        properties.set("bands", bandArrayProperty());
        properties.set("statistics", stringArrayProperty(
                "每个分区需要计算的统计量；不传时计算分析引擎的默认统计量",
                "count", "min", "max", "mean", "median", "sum", "stddev"));
        properties.set("noData", numberProperty("可选的无效像元值"));
        com.fasterxml.jackson.databind.node.ArrayNode required =
                (com.fasterxml.jackson.databind.node.ArrayNode) schema.get("required");
        required.add("rasterUri").add("zonesUri").add("zoneField");
        return schema;
    }

    @Override
    protected void validate(JsonNode arguments) {
        requireNonBlankString(arguments, "rasterUri");
        requireNonBlankString(arguments, "zonesUri");
        requireNonBlankString(arguments, "zoneField");
        validateOptionalBands(arguments);
        validateOptionalStringArray(arguments, "statistics");
        JsonNode noData = arguments.get("noData");
        if (noData != null && !noData.isNumber()) {
            throw new IllegalArgumentException("noData 必须是数值");
        }
    }
}
