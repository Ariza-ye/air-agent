package com.ariza.agent.tool.reflect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数
 *
 * @author ariza
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {

    /**
     * 获取对外暴露的工具参数名称。
     *
     * @return 工具参数名称
     */
    String value();

    /**
     * 获取供模型理解参数用途的说明。
     *
     * @return 参数用途说明
     */
    String description() default "";

    /**
     * 指示调用工具时是否必须提供此参数。
     *
     * @return 参数为必填项时返回 {@code true}
     */
    boolean required() default true;
}
