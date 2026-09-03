package com.ariza.agent.tool.reflect;

import java.lang.annotation.*;

/**
 * 描述工具返回对象中供模型理解的字段。
 *
 * <p>可以标注普通字段、Record 组件或 JavaBean getter。
 * {@link ReflectionToolFactory} 会扫描这些元数据并将返回值 JSON Schema 追加到工具说明中。</p>
 *
 * @author ariza
 */
@Documented
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolResultField {

    /**
     * 获取供模型理解字段用途的说明。
     *
     * @return 字段用途说明
     */
    String description();

    /**
     * 必定有值
     *
     * @return 必有值时,返回 {@code true}
     */
    boolean hasValue() default false;

    /**
     * 获取字段值的格式提示。
     *
     * @return 字段格式；默认为未指定
     */
    ToolFieldFormat format() default ToolFieldFormat.UNSPECIFIED;

    /**
     * 获取字段允许使用的值。
     *
     * @return 允许值列表；空数组表示不限制
     */
    String[] allowedValues() default {};
}
