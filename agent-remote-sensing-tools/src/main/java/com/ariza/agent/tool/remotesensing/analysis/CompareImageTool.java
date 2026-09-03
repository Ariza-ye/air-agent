package com.ariza.agent.tool.remotesensing.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 对两幅遥感影像执行定量对比。
 *
 * @author ariza
 */
public final class CompareImageTool extends AbstractAnalysisTool {
    /**
     * 模型调用时使用的工具名称。
     */
    public static final String NAME = "compareImage";

    /**
     * 创建影像对比工具。
     *
     * @param executor 实际执行影像对比任务的执行器
     */
    public CompareImageTool(AnalysisExecutor executor) {
        super(NAME, "对比基准影像和目标影像，计算差异或相似度指标", schema(), executor);
    }

    /**
     * 构建影像对比工具的输入参数 Schema。
     *
     * @return 输入参数 Schema
     */
    private static JsonNode schema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        properties.set("referenceRasterUri", stringProperty("基准栅格影像 URI 或文件路径"));
        properties.set("targetRasterUri", stringProperty("待对比栅格影像 URI 或文件路径"));
        properties.set("bands", bandArrayProperty());
        properties.set("metrics", stringArrayProperty(
                "对比指标；不传时由分析引擎选择默认指标",
                "difference", "mae", "rmse", "correlation", "ssim"));
        com.fasterxml.jackson.databind.node.ArrayNode required =
                (com.fasterxml.jackson.databind.node.ArrayNode) schema.get("required");
        required.add("referenceRasterUri").add("targetRasterUri");
        return schema;
    }

    @Override
    protected void validate(JsonNode arguments) {
        requireNonBlankString(arguments, "referenceRasterUri");
        requireNonBlankString(arguments, "targetRasterUri");
        validateOptionalBands(arguments);
        validateOptionalStringArray(arguments, "metrics");
    }
}
