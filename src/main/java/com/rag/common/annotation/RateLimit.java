package com.rag.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解，基于Redis滑动窗口实现。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流Key，用于区分不同的接口 */
    String key() default "default";

    /** 每秒允许的请求数 */
    int permitsPerSecond() default 100;

    /** 限流提示消息 */
    String message() default "请求过于频繁，请稍后再试";
}
