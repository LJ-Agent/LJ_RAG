package com.rag.controller.interceptor;

import com.rag.common.context.UserContext;

/**
 * JWT 鉴权拦截器 — 兼容旧代码的 CURRENT_USER_ID ThreadLocal。
 * 从 Spring SecurityContext 中提取当前用户 ID。
 */
public class JwtAuthInterceptor {

    public static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    /**
     * 在请求前由过滤器/拦截器设置。
     */
    public static void setCurrentUserId() {
        Long userId = UserContext.getUserId();
        CURRENT_USER_ID.set(userId != null ? userId : 0L);
    }

    /** 请求结束后清理。 */
    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
