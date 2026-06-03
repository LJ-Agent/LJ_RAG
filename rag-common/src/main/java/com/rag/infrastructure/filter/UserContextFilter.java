package com.rag.infrastructure.filter;

import com.rag.controller.interceptor.JwtAuthInterceptor;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 从 Gateway 传入的 X-User-Id 请求头提取用户 ID，设置 CURRENT_USER_ID。
 */
@Component
@Order(1)
public class UserContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpReq) {
                String userIdHeader = httpReq.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isEmpty()) {
                    try {
                        JwtAuthInterceptor.CURRENT_USER_ID.set(Long.parseLong(userIdHeader));
                    } catch (NumberFormatException ignored) {
                        JwtAuthInterceptor.CURRENT_USER_ID.set(0L);
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            JwtAuthInterceptor.clear();
        }
    }
}
