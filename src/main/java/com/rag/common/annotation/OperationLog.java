package com.rag.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解，记录用户的关键操作。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 模块名称 */
    String module();

    /** 操作类型 */
    String type();

    /** 操作描述 */
    String description() default "";
}
