package com.rag.service.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.domain.entity.SystemConfig;

public interface SystemConfigService {

    Result<Page<SystemConfig>> list(Integer page, Integer size);

    Result<SystemConfig> getByKey(String key);

    Result<Void> saveOrUpdate(SystemConfig config);

    Result<Void> delete(Long id);

    String getStringConfig(String key, String defaultValue);

    int getIntConfig(String key, int defaultValue);

    boolean getBoolConfig(String key, boolean defaultValue);
}
