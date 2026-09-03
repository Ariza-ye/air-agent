package com.ariza.agent.tool.reflect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法上的注解,用来标识工具能力
 *
 * @author ariza
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {

    /**
     * 获取对外暴露的工具名称。
     *
     * @return 工具名称；空字符串表示使用被标注的方法名
     */
    String name() default "";

    /**
     * 获取供模型理解工具用途的说明。
     *
     * @return 工具用途说明
     */
    String description();
}
