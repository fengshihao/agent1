package com.agent1.javaagent.tool.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具作者面。注册后变成运行面的 {@link com.agent1.javaagent.tool.AgentTool}，
 * 工具循环不直接调用注解方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    String name() default "";

    String description() default "";

    boolean readOnly() default false;

    boolean concurrencySafe() default false;

    /**
     * 本次调用希望的超时（毫秒）。0 表示沿用运行时默认值。
     * 运行时只会把超时抬高到至少这个值，不会压低于默认超时。
     */
    long timeoutMs() default 0;
}
