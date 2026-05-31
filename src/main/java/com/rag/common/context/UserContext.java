package com.rag.common.context;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

/**
 * 获取当前请求的用户信息（从 JWT SecurityContext 中提取）。
 */
public class UserContext {

    public static Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof Long) return (Long) principal;
            if (principal instanceof Number) return ((Number) principal).longValue();
            try { return Long.parseLong(principal.toString()); } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    public static String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getName();
        }
        return "system";
    }

    public static boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            return authorities.stream().anyMatch(a -> a.getAuthority().equals(permission));
        }
        return false;
    }
}
