package com.rag.controller.interceptor;

import com.rag.common.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window * 1000)
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, now, now .. '-' .. math.random())
                redis.call('EXPIRE', key, window + 1)
                return 1
            else
                return 0
            end
            """;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String key = "rate:" + rateLimit.key() + ":" + getClientIp(request);
        int permitsPerSecond = rateLimit.permitsPerSecond();
        long now = System.currentTimeMillis();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(script, List.of(key),
                String.valueOf(1), String.valueOf(permitsPerSecond), String.valueOf(now));

        if (result == null || result == 0) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"" + rateLimit.message() + "\"}");
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
