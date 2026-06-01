package com.rag.common.util;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE连接管理工具，用于维护活跃的SSE连接。
 */
public class SseEmitterUtil {

    private static final ConcurrentHashMap<String, SseEmitter> EMITTERS = new ConcurrentHashMap<>();

    public static void add(String sessionId, SseEmitter emitter) {
        EMITTERS.put(sessionId, emitter);
    }

    public static void remove(String sessionId) {
        SseEmitter emitter = EMITTERS.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    public static SseEmitter get(String sessionId) {
        return EMITTERS.get(sessionId);
    }

    public static Map<String, SseEmitter> getAll() {
        return EMITTERS;
    }

    public static int getActiveCount() {
        return EMITTERS.size();
    }
}
