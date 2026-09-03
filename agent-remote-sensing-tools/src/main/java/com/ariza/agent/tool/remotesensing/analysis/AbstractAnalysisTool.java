package com.ariza.agent.tool.remotesensing.analysis;

import com.ariza.agent.core.RunContext;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * 遥感分析工具的公共基类。
 *
 * <p>统一处理工具元数据、输入 Schema 防御性复制、基础参数校验以及执行器异常转换。</p>
 *
 * @author ariza
 */
abstract class AbstractAnalysisTool implements Tool {
    /**
     * 用于构建工具输入 Schema 的 JSON 节点工厂。
     */
    protected static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final String name;
    private final String description;
    private final JsonNode inputSchema;
    private final AnalysisExecutor executor;

    /**
     * 创建遥感分析工具。
     *
     * @param name        模型调用时使用的工具名称
     * @param description 工具用途说明
     * @param inputSchema 工具输入参数的 JSON Schema
     * @param executor    实际分析任务执行器
     */
    protected AbstractAnalysisTool(
            String name,
            String description,
            JsonNode inputSchema,
            AnalysisExecutor executor) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema.deepCopy();
        this.executor = Objects.requireNonNull(executor, "executor 不能为 null");
    }

    /**
     * 创建禁止未声明字段的对象类型 Schema。
     *
     * @return 对象类型 Schema
     */
    protected static ObjectNode objectSchema() {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", "object");
        schema.set("properties", JSON.objectNode());
        schema.set("required", JSON.arrayNode());
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * 创建非空字符串属性 Schema。
     *
     * @param description 属性说明
     * @return 字符串属性 Schema
     */
    protected static ObjectNode stringProperty(String description) {
        ObjectNode property = JSON.objectNode();
        property.put("type", "string");
        property.put("description", description);
        property.put("minLength", 1);
        return property;
    }

    /**
     * 创建数值属性 Schema。
     *
     * @param description 属性说明
     * @return 数值属性 Schema
     */
    protected static ObjectNode numberProperty(String description) {
        ObjectNode property = JSON.objectNode();
        property.put("type", "number");
        property.put("description", description);
        return property;
    }

    /**
     * 创建非空字符串数组属性 Schema。
     *
     * @param description 属性说明
     * @param values      可选值；为空时不限制枚举范围
     * @return 字符串数组属性 Schema
     */
    protected static ObjectNode stringArrayProperty(String description, String... values) {
        ObjectNode property = JSON.objectNode();
        property.put("type", "array");
        property.put("description", description);
        property.put("minItems", 1);
        property.put("uniqueItems", true);
        ObjectNode items = JSON.objectNode();
        items.put("type", "string");
        if (values.length > 0) {
            ArrayNode enumeration = JSON.arrayNode();
            for (String value : values) {
                enumeration.add(value);
            }
            items.set("enum", enumeration);
        }
        property.set("items", items);
        return property;
    }

    /**
     * 创建从 1 开始编号的波段数组属性 Schema。
     *
     * @return 波段数组属性 Schema
     */
    protected static ObjectNode bandArrayProperty() {
        ObjectNode property = JSON.objectNode();
        property.put("type", "array");
        property.put("description", "参与计算的波段编号，编号从 1 开始；不传时由分析引擎使用默认波段");
        property.put("minItems", 1);
        property.put("uniqueItems", true);
        ObjectNode items = JSON.objectNode();
        items.put("type", "integer");
        items.put("minimum", 1);
        property.set("items", items);
        return property;
    }

    /**
     * 校验指定字段为必填非空字符串。
     *
     * @param arguments 工具参数
     * @param field     字段名称
     */
    protected static void requireNonBlankString(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " 必须是非空字符串");
        }
    }

    /**
     * 校验可选的 {@code bands} 字段。
     *
     * @param arguments 工具参数
     */
    protected static void validateOptionalBands(JsonNode arguments) {
        JsonNode bands = arguments.get("bands");
        if (bands == null) {
            return;
        }
        if (!bands.isArray() || bands.isEmpty()) {
            throw new IllegalArgumentException("bands 必须是非空数组");
        }
        for (JsonNode band : bands) {
            if (!band.isIntegralNumber() || !band.canConvertToInt() || band.intValue() < 1) {
                throw new IllegalArgumentException("bands 中的波段编号必须是大于等于 1 的整数");
            }
        }
    }

    /**
     * 校验可选的非空字符串数组字段。
     *
     * @param arguments 工具参数
     * @param field     字段名称
     */
    protected static void validateOptionalStringArray(JsonNode arguments, String field) {
        JsonNode values = arguments.get(field);
        if (values == null) {
            return;
        }
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalArgumentException(field + " 必须是非空数组");
        }
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalArgumentException(field + " 中的值必须是非空字符串");
            }
        }
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final String description() {
        return description;
    }

    @Override
    public final JsonNode inputSchema() {
        return inputSchema.deepCopy();
    }

    @Override
    public final ToolResult call(JsonNode arguments, RunContext context) {
        if (arguments == null || !arguments.isObject()) {
            return ToolResult.failure("参数必须是 JSON 对象");
        }
        if (context == null) {
            return ToolResult.failure("运行上下文不能为 null");
        }
        try {
            validate(arguments);
            ToolResult result = executor.execute(name, arguments.deepCopy(), context);
            return result == null ? ToolResult.failure("分析执行器返回了 null") : result;
        } catch (IllegalArgumentException exception) {
            return ToolResult.failure(exception.getMessage());
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return ToolResult.failure(message == null || message.isBlank()
                    ? "遥感分析执行失败"
                    : "遥感分析执行失败: " + message);
        }
    }

    /**
     * 校验具体工具的业务参数。
     *
     * @param arguments 模型提供的 JSON 参数
     * @throws IllegalArgumentException 参数不符合要求时抛出
     */
    protected abstract void validate(JsonNode arguments);
}
