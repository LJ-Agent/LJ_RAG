package com.rag.service.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.domain.entity.ConfigHistory;
import com.rag.domain.entity.SystemConfig;

import java.util.List;
import java.util.Map;

public interface SystemConfigService {

    // ─── 校验 ───
    String validateConfigValue(SystemConfig config);

    // ─── CRUD ───
    Result<Page<SystemConfig>> list(Integer page, Integer size, String category);

    Result<List<SystemConfig>> listByCategory(String category);

    Result<SystemConfig> getByKey(String key);

    /** 保存或更新配置 — 包含校验、历史记录、Kafka发布 */
    Result<Map<String, Object>> saveOrUpdateConfig(SystemConfig config, Long userId, String username);

    /** 批量更新 */
    Result<Map<String, Object>> batchUpdate(List<SystemConfig> configs, Long userId, String username);

    /** 清除缓存 */
    void clearCache(String configKey);

    Result<Void> delete(Long id);

    // ─── 历史与回滚 ───
    Result<List<ConfigHistory>> getHistory(String configKey);

    Result<Map<String, Object>> rollback(String configKey, Long historyId, Long userId, String username);

    // ─── 类型安全读取 ───
    String getStringConfig(String key, String defaultValue);

    int getIntConfig(String key, int defaultValue);

    boolean getBoolConfig(String key, boolean defaultValue);

    double getDoubleConfig(String key, double defaultValue);
}
