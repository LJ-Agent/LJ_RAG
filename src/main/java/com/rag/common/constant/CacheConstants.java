package com.rag.common.constant;

public interface CacheConstants {

    /** 问答缓存前缀 */
    String QA_CACHE_PREFIX = "qa:cache:";

    /** 系统配置缓存Key */
    String SYSTEM_CONFIG_KEY = "system:config";

    /** 用户Session前缀 */
    String USER_SESSION_PREFIX = "user:session:";

    /** 限流Key前缀 */
    String RATE_LIMIT_PREFIX = "rate:";

    /** 知识库列表缓存前缀 */
    String KB_LIST_PREFIX = "kb:list:";

    /** 问答缓存TTL（秒） */
    long QA_CACHE_TTL = 3600L;

    /** 系统配置缓存TTL（秒） */
    long SYSTEM_CONFIG_TTL = 600L;

    /** 知识库列表缓存TTL（秒） */
    long KB_LIST_TTL = 1800L;
}
