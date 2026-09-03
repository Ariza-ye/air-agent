package com.ariza.agent.tool.remotesensing.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * 根据波段表达式计算遥感指数。
 *
 * @author ariza
 */
public final class CalcIndexTool extends AbstractAnalysisTool {
    /**
     * 模型调用时使用的工具名称。
     */
    public static final String NAME = "calcIndex";

    /**
     * 创建遥感指数计算工具。
     *
     * @param executor 实际执行指数计算任务的执行器
     */
    public CalcIndexTool(AnalysisExecutor executor) {
        super(NAME, "使用波段表达式计算 NDVI、NDWI 等遥感指数", schema(), executor);
    }

    /**
     * 构建遥感指数计算工具的输入参数 Schema。
     *
     * @return 输入参数 Schema
     */
    private static JsonNode schema() {
        ObjectNode schema = objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        properties.set("rasterUri", stringProperty("输入栅格影像 URI 或分析引擎可访问的文件路径"));
        properties.set("expression", stringProperty("指数表达式，例如 (nir-red)/(nir+red)"));
        ObjectNode mapping = JSON.objectNode();
        mapping.put("type", "object");
        mapping.put("description", "表达式变量到波段编号的映射，例如 {\"nir\":4,\"red\":3}");
        mapping.put("minProperties", 1);
        ObjectNode bandNumber = JSON.objectNode();
        bandNumber.put("type", "integer");
        bandNumber.put("minimum", 1);
        mapping.set("additionalProperties", bandNumber);
        properties.set("bandMapping", mapping);
        properties.set("outputUri", stringProperty("可选的结果输出 URI 或文件路径"));
        com.fasterxml.jackson.databind.node.ArrayNode required =
                (com.fasterxml.jackson.databind.node.ArrayNode) schema.get("required");
        required.add("rasterUri").add("expression").add("bandMapping");
        return schema;
    }

    @Override
    protected void validate(JsonNode arguments) {
        requireNonBlankString(arguments, "rasterUri");
        requireNonBlankString(arguments, "expression");
        JsonNode bandMapping = arguments.get("bandMapping");
        if (bandMapping == null || !bandMapping.isObject() || bandMapping.isEmpty()) {
            throw new IllegalArgumentException("bandMapping 必须是非空对象");
        }
        for (Map.Entry<String, JsonNode> field : bandMapping.properties()) {
            if (field.getKey().isBlank()
                    || !field.getValue().isIntegralNumber()
                    || !field.getValue().canConvertToInt()
                    || field.getValue().intValue() < 1) {
                throw new IllegalArgumentException("bandMapping 的值必须是大于等于 1 的波段编号");
            }
        }
        JsonNode outputUri = arguments.get("outputUri");
        if (outputUri != null && (!outputUri.isTextual() || outputUri.asText().isBlank())) {
            throw new IllegalArgumentException("outputUri 必须是非空字符串");
        }
    }
}
