package com.ariza.agent.tool.reflect;

import com.ariza.agent.core.RunContext;
import com.ariza.agent.core.tool.Tool;
import com.ariza.agent.core.tool.ToolResult;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/**
 * 工具创建工厂
 *
 * @author ariza
 */
public final class ReflectionToolFactory {

    private static final String RESULT_SCHEMA_PREFIX = "\n返回值结构（JSON Schema）：\n";

    private final ObjectMapper objectMapper;

    /**
     * 使用默认 Jackson 配置创建反射工具工厂。
     */
    public ReflectionToolFactory() {
        this(new ObjectMapper());
    }

    /**
     * 使用指定 Jackson 配置创建反射工具工厂。
     *
     * @param objectMapper 用于参数转换和结果序列化的对象映射器
     */
    public ReflectionToolFactory(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * 扫描对象中标注为智能体工具的方法并创建工具定义。
     *
     * @param bean 包含工具方法的对象
     * @return 从对象方法生成的工具列表
     * @throws NullPointerException     当对象为 {@code null} 时抛出
     * @throws IllegalArgumentException 当工具声明无效或工具名称重复时抛出
     */
    public List<Tool> create(Object bean) {
        Objects.requireNonNull(bean, "bean");

        List<Method> methods = findToolMethods(bean.getClass());
        List<Tool> tools = new ArrayList<>(methods.size());
        Set<String> names = new HashSet<>();
        for (Method method : methods) {
            AgentTool annotation = method.getAnnotation(AgentTool.class);
            String name = annotation.name().isBlank() ? method.getName() : annotation.name().trim();
            if (!names.add(name)) {
                throw new IllegalArgumentException("工具名称重复: " + name);
            }
            tools.add(createTool(bean, method, annotation, name));
        }
        return List.copyOf(tools);
    }

    /**
     * 从当前类型及其父类中查找有效的工具方法。
     *
     * <p>子类声明的方法会遮蔽父类中的同签名方法，桥接方法和编译器生成的方法会被忽略。</p>
     *
     * @param beanType 要扫描的对象类型
     * @return 按方法签名稳定排序的工具方法列表
     */
    private List<Method> findToolMethods(Class<?> beanType) {
        List<Method> methods = new ArrayList<>();
        Set<String> signatures = new HashSet<>();
        for (Class<?> type = beanType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                String signature = method.getName() + List.of(method.getParameterTypes());
                if (signatures.add(signature) && method.isAnnotationPresent(AgentTool.class)) {
                    methods.add(method);
                }
            }
        }
        methods.sort(Comparator.comparing(Method::toGenericString));
        return methods;
    }

    /**
     * 根据工具方法及其注解创建可执行的工具实例。
     *
     * @param bean       工具方法所属对象
     * @param method     被 {@link AgentTool} 标注的方法
     * @param annotation 工具方法注解
     * @param name       对模型暴露的工具名称
     * @return 可供 Agent 注册的工具实例
     * @throws IllegalArgumentException 当说明为空、方法不可访问或参数声明无效时抛出
     */
    private Tool createTool(Object bean, Method method, AgentTool annotation, String name) {
        if (name.isBlank()) {
            throw invalid(method, "工具名称不能为空");
        }
        if (annotation.description().isBlank()) {
            throw invalid(method, "工具说明不能为空");
        }
        Object invocationTarget = Modifier.isStatic(method.getModifiers()) ? null : bean;
        if (!method.canAccess(invocationTarget) && !method.trySetAccessible()) {
            throw invalid(method, "方法不可访问");
        }

        ParameterBinding[] bindings = createBindings(method);
        JsonNode inputSchema = createInputSchema(bindings);
        ResultSchema resultSchema = createResultSchema(method.getGenericReturnType());
        String description = annotation.description().trim();
        if (resultSchema.documented()) {
            description += RESULT_SCHEMA_PREFIX + resultSchema.schema().toPrettyString();
        }
        return new ReflectedTool(
                invocationTarget,
                method,
                name,
                description,
                inputSchema,
                bindings
        );
    }

    /**
     * 解析方法参数并创建参数绑定信息。
     *
     * <p>未标注 {@link ToolParam} 的 {@link RunContext} 会被识别为运行上下文参数，
     * 其余参数必须使用 {@link ToolParam} 声明。</p>
     *
     * @param method 要解析的工具方法
     * @return 与方法参数顺序一致的参数绑定数组
     * @throws IllegalArgumentException 当参数注解缺失、名称重复或可选参数使用基本类型时抛出
     */
    private ParameterBinding[] createBindings(Method method) {
        Parameter[] parameters = method.getParameters();
        ParameterBinding[] bindings = new ParameterBinding[parameters.length];
        Set<String> names = new HashSet<>();

        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            ToolParam annotation = parameter.getAnnotation(ToolParam.class);
            if (parameter.getType() == RunContext.class && annotation == null) {
                bindings[index] = ParameterBinding.runContext();
                continue;
            }
            if (annotation == null) {
                throw invalid(method, "参数 " + parameter.getName() + " 缺少 @ToolParam");
            }

            String name = annotation.value().trim();
            if (name.isBlank()) {
                throw invalid(method, "参数名称不能为空");
            }
            if (!names.add(name)) {
                throw invalid(method, "参数名称重复: " + name);
            }
            if (!annotation.required() && parameter.getType().isPrimitive()) {
                throw invalid(method, "可选参数不能使用基本类型: " + name);
            }
            bindings[index] = ParameterBinding.argument(
                    name,
                    annotation.description().trim(),
                    annotation.required(),
                    parameter.getParameterizedType()
            );
        }
        return bindings;
    }

    /**
     * 根据参数绑定创建工具输入 JSON Schema。
     *
     * @param bindings 工具方法参数绑定
     * @return 描述工具输入对象的 JSON Schema
     */
    private JsonNode createInputSchema(ParameterBinding[] bindings) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (ParameterBinding binding : bindings) {
            if (binding.context()) {
                continue;
            }
            ObjectNode property = schemaFor(binding.type(), new HashSet<>());
            if (!binding.description().isBlank()) {
                property.put("description", binding.description());
            }
            properties.set(binding.name(), property);
            if (binding.required()) {
                required.add(binding.name());
            }
        }
        return schema;
    }

    /**
     * 根据工具方法返回类型及其 {@link ToolResultField} 注解生成返回值结构。
     *
     * @param returnType 工具方法的泛型返回类型
     * @return 返回值结构及是否发现字段说明
     */
    private ResultSchema createResultSchema(Type returnType) {
        return resultSchemaFor(returnType, new HashSet<>());
    }

    /**
     * 递归扫描返回类型中的字段说明。
     *
     * <p>属性名称通过 Jackson 的序列化元数据获取，因此能够兼容 Jackson 的属性命名配置。
     * 未被 Jackson 识别为可序列化属性的字段不会暴露给模型。</p>
     *
     * @param type     当前返回类型
     * @param visiting 当前递归路径中正在处理的类型
     * @return 当前类型的 JSON Schema 及是否包含字段说明
     */
    private ResultSchema resultSchemaFor(Type type, Set<Type> visiting) {
        Class<?> rawType = rawType(type);
        if (rawType == null || rawType == void.class || rawType == Void.class
                || ToolResult.class.isAssignableFrom(rawType)) {
            return new ResultSchema(schemaFor(type, new HashSet<>()), false);
        }
        if (rawType.isArray() || Collection.class.isAssignableFrom(rawType)) {
            ResultSchema items = resultSchemaFor(elementType(type, rawType), visiting);
            ObjectNode schema = JsonNodeFactory.instance.objectNode().put("type", "array");
            schema.set("items", items.schema());
            return new ResultSchema(schema, items.documented());
        }
        if (Map.class.isAssignableFrom(rawType)) {
            ResultSchema values = resultSchemaFor(mapValueType(type), visiting);
            ObjectNode schema = JsonNodeFactory.instance.objectNode().put("type", "object");
            schema.set("additionalProperties", values.schema());
            return new ResultSchema(schema, values.documented());
        }
        if (isSimpleType(rawType)) {
            return new ResultSchema(schemaFor(type, new HashSet<>()), false);
        }
        if (!visiting.add(type)) {
            return new ResultSchema(JsonNodeFactory.instance.objectNode().put("type", "object"), false);
        }

        try {
            ObjectNode schema = JsonNodeFactory.instance.objectNode()
                    .put("type", "object")
                    .put("additionalProperties", false);
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = schema.putArray("required");
            boolean documented = false;

            JavaType javaType = objectMapper.getTypeFactory().constructType(type);
            for (BeanPropertyDefinition property : objectMapper.getSerializationConfig()
                    .introspect(javaType)
                    .findProperties()) {
                AnnotatedMember member = annotatedResultMember(property);
                ToolResultField annotation = member == null
                        ? null
                        : member.getAnnotation(ToolResultField.class);
                Type propertyType = propertyType(member, property);
                ResultSchema nested = resultSchemaFor(propertyType, visiting);
                if (annotation == null && !nested.documented()) {
                    continue;
                }

                ObjectNode propertySchema = nested.schema().deepCopy();
                if (annotation != null) {
                    applyResultField(propertySchema, annotation);
                    if (annotation.hasValue()) {
                        required.add(property.getName());
                    }
                }
                properties.set(property.getName(), propertySchema);
                documented = true;
            }
            return new ResultSchema(schema, documented);
        } finally {
            visiting.remove(type);
        }
    }

    /**
     * 查找属性上携带返回字段注解的 Jackson 成员。
     */
    private AnnotatedMember annotatedResultMember(BeanPropertyDefinition property) {
        AnnotatedMember[] candidates = {
                property.getField(),
                property.getGetter(),
                property.getConstructorParameter()
        };
        for (AnnotatedMember candidate : candidates) {
            if (candidate != null && candidate.hasAnnotation(ToolResultField.class)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 获取属性类型；优先使用标注注解的成员，否则使用 Jackson 的主要成员。
     */
    private Type propertyType(AnnotatedMember annotatedMember, BeanPropertyDefinition property) {
        AnnotatedMember member = annotatedMember == null ? property.getPrimaryMember() : annotatedMember;
        return member == null ? Object.class : member.getType();
    }

    /**
     * 将返回字段注解声明的元数据写入 JSON Schema。
     */
    private void applyResultField(ObjectNode schema, ToolResultField annotation) {
        schema.put("description", annotation.description().trim());
        if (annotation.format() != ToolFieldFormat.UNSPECIFIED) {
            schema.put("format", annotation.format().value());
            schema.put("example", annotation.format().example());
        }
        if (annotation.allowedValues().length > 0) {
            ArrayNode allowedValues = schema.putArray("enum");
            for (String value : annotation.allowedValues()) {
                allowedValues.add(value);
            }
        }
    }

    /**
     * 判断类型是否无需继续扫描对象属性。
     */
    private boolean isSimpleType(Class<?> rawType) {
        return rawType.isPrimitive()
                || rawType == Object.class
                || rawType == String.class
                || rawType == Character.class
                || Number.class.isAssignableFrom(rawType)
                || rawType == Boolean.class
                || rawType == UUID.class
                || rawType.isEnum()
                || JsonNode.class.isAssignableFrom(rawType)
                || TemporalAccessor.class.isAssignableFrom(rawType);
    }

    /**
     * 将 Java 类型递归转换为对应的 JSON Schema。
     *
     * @param type     Java 参数类型
     * @param visiting 当前递归路径中正在处理的类型，用于避免循环引用
     * @return 描述该 Java 类型的 JSON Schema
     */
    private ObjectNode schemaFor(Type type, Set<Type> visiting) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        Class<?> rawType = rawType(type);

        if (rawType == null || rawType == Object.class || JsonNode.class.isAssignableFrom(rawType)) {
            return schema;
        }
        if (rawType == String.class || rawType == Character.class || rawType == char.class
                || rawType == UUID.class || TemporalAccessor.class.isAssignableFrom(rawType)) {
            return schema.put("type", "string");
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return schema.put("type", "boolean");
        }
        if (rawType == byte.class || rawType == Byte.class
                || rawType == short.class || rawType == Short.class
                || rawType == int.class || rawType == Integer.class
                || rawType == long.class || rawType == Long.class
                || rawType == BigInteger.class) {
            return schema.put("type", "integer");
        }
        if (rawType == float.class || rawType == Float.class
                || rawType == double.class || rawType == Double.class
                || rawType == BigDecimal.class || rawType == Number.class) {
            return schema.put("type", "number");
        }
        if (rawType.isEnum()) {
            schema.put("type", "string");
            ArrayNode values = schema.putArray("enum");
            for (Object value : rawType.getEnumConstants()) {
                values.add(((Enum<?>) value).name());
            }
            return schema;
        }
        if (rawType.isArray() || Collection.class.isAssignableFrom(rawType)) {
            schema.put("type", "array");
            schema.set("items", schemaFor(elementType(type, rawType), visiting));
            return schema;
        }
        if (Map.class.isAssignableFrom(rawType)) {
            schema.put("type", "object");
            schema.set("additionalProperties", schemaFor(mapValueType(type), visiting));
            return schema;
        }
        if (!visiting.add(type)) {
            return schema.put("type", "object");
        }
        try {
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = schema.putArray("required");
            if (rawType.isRecord()) {
                for (var component : rawType.getRecordComponents()) {
                    properties.set(component.getName(), schemaFor(component.getGenericType(), visiting));
                    if (component.getType().isPrimitive()) {
                        required.add(component.getName());
                    }
                }
            }
            return schema;
        } finally {
            visiting.remove(type);
        }
    }

    /**
     * 获取数组或集合的元素类型。
     *
     * @param type    完整泛型类型
     * @param rawType 原始类型
     * @return 元素类型；无法确定时返回 {@link Object}
     */
    private Type elementType(Type type, Class<?> rawType) {
        if (rawType.isArray()) {
            return rawType.getComponentType();
        }
        if (type instanceof GenericArrayType arrayType) {
            return arrayType.getGenericComponentType();
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments()[0];
        }
        if (type instanceof JavaType javaType && javaType.containedTypeCount() > 0) {
            return javaType.containedType(0);
        }
        return Object.class;
    }

    /**
     * 获取 Map 值的泛型类型。
     *
     * @param type Map 的完整泛型类型
     * @return Map 值类型；无法确定时返回 {@link Object}
     */
    private Type mapValueType(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments()[1];
        }
        if (type instanceof JavaType javaType && javaType.containedTypeCount() > 1) {
            return javaType.containedType(1);
        }
        return Object.class;
    }

    /**
     * 从反射类型中解析原始 Java 类。
     *
     * @param type 要解析的反射类型
     * @return 对应的原始类；无法解析时返回 {@code null}
     */
    private Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof GenericArrayType) {
            return Array.newInstance(Object.class, 0).getClass();
        }
        if (type instanceof JavaType javaType) {
            return javaType.getRawClass();
        }
        return null;
    }

    /**
     * 创建包含具体方法签名的声明异常。
     *
     * @param method  声明无效的方法
     * @param message 错误说明
     * @return 参数声明异常
     */
    private IllegalArgumentException invalid(Method method, String message) {
        return new IllegalArgumentException(message + ": " + method.toGenericString());
    }

    /**
     * @author ariza
     */
    private record ParameterBinding(String name,
                                    String description,
                                    boolean required,
                                    Type type,
                                    boolean context) {
        /**
         * 创建模型输入参数绑定。
         *
         * @param name        参数名称
         * @param description 参数说明
         * @param required    是否必填
         * @param type        Java 参数类型
         * @return 模型输入参数绑定
         */
        private static ParameterBinding argument(String name,
                                                 String description,
                                                 boolean required,
                                                 Type type) {
            return new ParameterBinding(name, description, required, type, false);
        }

        /**
         * 创建运行上下文参数绑定。
         *
         * @return 不会暴露给模型的运行上下文绑定
         */
        private static ParameterBinding runContext() {
            return new ParameterBinding(null, "", false, RunContext.class, true);
        }
    }

    /**
     * @author ariza
     */
    private record ResultSchema(ObjectNode schema, boolean documented) {
    }

    /**
     * @author ariza
     */
    private final class ReflectedTool implements Tool {
        private final Object bean;
        private final Method method;
        private final String name;
        private final String description;
        private final JsonNode inputSchema;
        private final ParameterBinding[] bindings;

        /**
         * 创建基于 Java 方法的工具实现。
         *
         * @param bean        方法调用目标；静态方法为 {@code null}
         * @param method      实际调用的方法
         * @param name        工具名称
         * @param description 工具说明
         * @param inputSchema 输入 JSON Schema
         * @param bindings    方法参数绑定
         */
        private ReflectedTool(Object bean,
                              Method method,
                              String name,
                              String description,
                              JsonNode inputSchema,
                              ParameterBinding[] bindings) {
            this.bean = bean;
            this.method = method;
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.bindings = bindings;
        }

        /**
         * 获取对模型暴露的工具名称。
         *
         * @return 工具名称
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * 获取供模型理解工具用途的说明。
         *
         * @return 工具说明
         */
        @Override
        public String description() {
            return description;
        }

        /**
         * 获取工具输入参数结构的独立副本。
         *
         * @return 输入 JSON Schema 副本
         */
        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        /**
         * 转换模型参数、调用 Java 方法并将返回值转换为工具结果。
         *
         * @param arguments 模型提供的 JSON 参数
         * @param context   当前运行上下文
         * @return 工具执行结果；参数或方法调用失败时返回失败结果
         */
        @Override
        public ToolResult call(JsonNode arguments, RunContext context) {
            if (arguments == null || !arguments.isObject()) {
                return ToolResult.failure("工具参数必须是 JSON 对象");
            }
            if (context == null) {
                return ToolResult.failure("运行上下文不能为空");
            }

            Object[] values = new Object[bindings.length];
            try {
                for (int index = 0; index < bindings.length; index++) {
                    ParameterBinding binding = bindings[index];
                    if (binding.context()) {
                        values[index] = context;
                        continue;
                    }
                    JsonNode value = arguments.get(binding.name());
                    if (value == null || value.isNull()) {
                        if (binding.required()) {
                            return ToolResult.failure("缺少必填参数: " + binding.name());
                        }
                        values[index] = null;
                        continue;
                    }
                    JavaType targetType = objectMapper.getTypeFactory().constructType(binding.type());
                    values[index] = objectMapper.convertValue(value, targetType);
                }

                Object result = method.invoke(bean, values);
                if (result instanceof ToolResult toolResult) {
                    return toolResult;
                }
                return ToolResult.success(objectMapper.valueToTree(result));
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                return ToolResult.failure(errorMessage(cause));
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                return ToolResult.failure(errorMessage(e));
            }
        }

        /**
         * 提取适合返回给模型的异常说明。
         *
         * @param throwable 工具执行过程中产生的异常
         * @return 非空错误说明
         */
        private String errorMessage(Throwable throwable) {
            String message = throwable.getMessage();
            return message == null || message.isBlank()
                    ? throwable.getClass().getSimpleName()
                    : message;
        }
    }
}
