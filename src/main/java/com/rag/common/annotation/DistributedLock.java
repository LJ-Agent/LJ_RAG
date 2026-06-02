package com.rag.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解，基于Redisson实现。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** 锁Key，支持SpEL表达式 */
    String key();

    /** 等待获取锁的时间 */
    long waitTime() default 3;

    /** 锁的持有时间 */
    long leaseTime() default 10;

    /** 时间单位 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
