package com.rag.service.config.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.domain.entity.SystemConfig;
import com.rag.domain.mapper.SystemConfigMapper;
import com.rag.service.config.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

    @Override
    public Result<Page<SystemConfig>> list(Integer page, Integer size) {
        Page<SystemConfig> pg = new Page<>(page, size);
        return Result.success(systemConfigMapper.selectPage(pg,
                new LambdaQueryWrapper<SystemConfig>().orderByAsc(SystemConfig::getConfigKey)));
    }

    @Override
    @Cacheable(value = "systemConfig", key = "#key")
    public Result<SystemConfig> getByKey(String key) {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        return Result.success(config);
    }

    @Override
    @CacheEvict(value = "systemConfig", key = "#config.configKey")
    public Result<Void> saveOrUpdate(SystemConfig config) {
        SystemConfig exist = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, config.getConfigKey()));
        if (exist != null) {
            exist.setConfigValue(config.getConfigValue());
            exist.setConfigType(config.getConfigType());
            exist.setDescription(config.getDescription());
            systemConfigMapper.updateById(exist);
        } else {
            systemConfigMapper.insert(config);
        }
        return Result.success();
    }

    @Override
    public Result<Void> delete(Long id) {
        systemConfigMapper.deleteById(id);
        return Result.success();
    }

    @Override
    public String getStringConfig(String key, String defaultValue) {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        return config != null ? config.getConfigValue() : defaultValue;
    }

    @Override
    public int getIntConfig(String key, int defaultValue) {
        String value = getStringConfig(key, null);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    @Override
    public boolean getBoolConfig(String key, boolean defaultValue) {
        String value = getStringConfig(key, null);
        if (value != null) {
            return "true".equalsIgnoreCase(value) || "1".equals(value);
        }
        return defaultValue;
    }
}
