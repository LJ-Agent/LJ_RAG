package com.rag.service.config.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.communication.kafka.dto.ConfigChangeEvent;
import com.rag.domain.entity.ConfigHistory;
import com.rag.domain.entity.SystemConfig;
import com.rag.domain.mapper.ConfigHistoryMapper;
import com.rag.domain.mapper.SystemConfigMapper;
import com.rag.service.config.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper configMapper;
    private final ConfigHistoryMapper historyMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String CONFIG_CHANGE_TOPIC = "rag-config-change";

    // ─── 校验引擎 ─────────────────────────────────────

    @Override
    public String validateConfigValue(SystemConfig config) {
        String ruleJson = config.getValidationRule();
        // 如果请求没带校验规则，从 DB 中查找
        if ((ruleJson == null || ruleJson.isBlank()) && config.getConfigKey() != null) {
            SystemConfig dbConfig = configMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, config.getConfigKey()));
            if (dbConfig != null) {
                ruleJson = dbConfig.getValidationRule();
                config.setConfigType(dbConfig.getConfigType()); // Use DB type
            }
        }
        return validateValue(config.getConfigValue(), config.getConfigType(), ruleJson);
    }

    private String validateValue(String value, String configType, String ruleJson) {
        if (value == null || value.isBlank()) return "配置值不能为空";
        if (ruleJson == null || ruleJson.isBlank()) return null; // 无校验规则则放行

        try {
            Map<String, Object> rule = JSONUtil.toBean(ruleJson, Map.class);
            String type = (String) rule.getOrDefault("type", configType.toLowerCase());

            return switch (type) {
                case "int" -> validateInt(value, rule);
                case "float" -> validateFloat(value, rule);
                case "bool" -> validateBool(value);
                case "enum" -> validateEnum(value, rule);
                case "pattern" -> validatePattern(value, rule);
                default -> null;
            };
        } catch (Exception e) {
            return null; // 规则解析失败不阻塞保存
        }
    }

    private String validateInt(String value, Map<String, Object> rule) {
        try {
            int v = Integer.parseInt(value);
            if (rule.containsKey("min") && v < ((Number) rule.get("min")).intValue())
                return "值不能小于 " + rule.get("min");
            if (rule.containsKey("max") && v > ((Number) rule.get("max")).intValue())
                return "值不能大于 " + rule.get("max");
            return null;
        } catch (NumberFormatException e) {
            return "请输入有效的整数";
        }
    }

    private String validateFloat(String value, Map<String, Object> rule) {
        try {
            double v = Double.parseDouble(value);
            if (rule.containsKey("min") && v < ((Number) rule.get("min")).doubleValue())
                return "值不能小于 " + rule.get("min");
            if (rule.containsKey("max") && v > ((Number) rule.get("max")).doubleValue())
                return "值不能大于 " + rule.get("max");
            return null;
        } catch (NumberFormatException e) {
            return "请输入有效的数字";
        }
    }

    private String validateBool(String value) {
        return ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) ? null : "请输入 true 或 false";
    }

    private String validateEnum(String value, Map<String, Object> rule) {
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) rule.getOrDefault("values", List.of());
        return values.contains(value) ? null : "值必须是: " + String.join(", ", values);
    }

    private String validatePattern(String value, Map<String, Object> rule) {
        String regex = (String) rule.get("regex");
        return (regex != null && value.matches(regex)) ? null : "值格式不正确";
    }

    // ─── CRUD + 校验 + 发布 ─────────────────────────────

    @Override
    public Result<Page<SystemConfig>> list(Integer page, Integer size, String category) {
        Page<SystemConfig> pg = new Page<>(page, size);
        LambdaQueryWrapper<SystemConfig> qw = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            qw.eq(SystemConfig::getCategory, category);
        }
        qw.orderByAsc(SystemConfig::getSortOrder, SystemConfig::getConfigKey);
        return Result.success(configMapper.selectPage(pg, qw));
    }

    @Override
    public Result<List<SystemConfig>> listByCategory(String category) {
        return Result.success(configMapper.selectList(
                new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getCategory, category)
                        .orderByAsc(SystemConfig::getSortOrder)));
    }

    @Override
    @Cacheable(value = "systemConfig", key = "#key")
    public Result<SystemConfig> getByKey(String key) {
        SystemConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        return Result.success(config);
    }

    @Override
    @Transactional
    public Result<Map<String, Object>> saveOrUpdateConfig(SystemConfig config, Long userId, String username) {
        // 1. 校验
        String error = validateConfigValue(config);
        if (error != null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "参数校验失败: " + error);
        }

        // 2. 查找已有记录
        SystemConfig exist = configMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, config.getConfigKey()));

        String oldValue = null;
        boolean isUpdate = exist != null;

        if (isUpdate) {
            oldValue = exist.getConfigValue();
            exist.setConfigValue(config.getConfigValue());
            if (config.getDescription() != null) exist.setDescription(config.getDescription());
            configMapper.updateById(exist);
            config.setId(exist.getId());
        } else {
            if (config.getConfigType() == null) config.setConfigType("STRING");
            if (config.getCategory() == null) config.setCategory("general");
            if (config.getReloadStrategy() == null) config.setReloadStrategy("kafka");
            configMapper.insert(config);
        }

        // 3. 记录历史
        ConfigHistory history = new ConfigHistory();
        history.setConfigId(isUpdate ? exist.getId() : config.getId());
        history.setConfigKey(config.getConfigKey());
        history.setOldValue(oldValue);
        history.setNewValue(config.getConfigValue());
        history.setChangedBy(userId);
        history.setChangedByName(username);
        history.setChangedAt(LocalDateTime.now());
        historyMapper.insert(history);

        // 4. 清除缓存
        clearCache(config.getConfigKey());

        // 5. 发布 Kafka 配置变更事件
        publishConfigChange(config, oldValue, userId, username);

        // 6. 返回
        Map<String, Object> result = new HashMap<>();
        result.put("configKey", config.getConfigKey());
        result.put("newValue", config.getConfigValue());
        result.put("reloadStrategy", config.getReloadStrategy() != null ? config.getReloadStrategy() : "kafka");
        result.put("isUpdate", isUpdate);
        result.put("validated", true);
        return Result.success(result);
    }

    @Override
    @Transactional
    public Result<Map<String, Object>> batchUpdate(List<SystemConfig> configs, Long userId, String username) {
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        for (SystemConfig c : configs) {
            try {
                Result<Map<String, Object>> r = saveOrUpdateConfig(c, userId, username);
                results.add(r.getData());
            } catch (BusinessException e) {
                errors.add(c.getConfigKey() + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", results.size());
        result.put("errors", errors);
        return Result.success(result);
    }

    @Override
    @CacheEvict(value = "systemConfig", key = "#configKey")
    public void clearCache(String configKey) {
        log.debug("Cache cleared for config: {}", configKey);
    }

    @Override
    public Result<Void> delete(Long id) {
        SystemConfig config = configMapper.selectById(id);
        if (config != null) {
            clearCache(config.getConfigKey());
        }
        configMapper.deleteById(id);
        return Result.success();
    }

    @Override
    public Result<List<ConfigHistory>> getHistory(String configKey) {
        return Result.success(historyMapper.selectList(
                new LambdaQueryWrapper<ConfigHistory>()
                        .eq(ConfigHistory::getConfigKey, configKey)
                        .orderByDesc(ConfigHistory::getChangedAt)
                        .last("LIMIT 50")));
    }

    @Override
    @Transactional
    public Result<Map<String, Object>> rollback(String configKey, Long historyId, Long userId, String username) {
        ConfigHistory history = historyMapper.selectById(historyId);
        if (history == null || !history.getConfigKey().equals(configKey)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "历史记录不存在");
        }
        SystemConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, configKey));
        if (config == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "配置不存在");
        }

        String rollbackValue = history.getOldValue() != null ? history.getOldValue() : config.getDefaultVal();
        if (rollbackValue == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "无可回滚的值");
        }

        config.setConfigValue(rollbackValue);
        return saveOrUpdateConfig(config, userId, username);
    }

    // ─── Kafka 事件发布 ────────────────────────────────

    private void publishConfigChange(SystemConfig config, String oldValue, Long userId, String username) {
        try {
            ConfigChangeEvent event = ConfigChangeEvent.builder()
                    .configKey(config.getConfigKey())
                    .oldValue(oldValue)
                    .newValue(config.getConfigValue())
                    .valueType(config.getConfigType())
                    .category(config.getCategory())
                    .reloadStrategy(config.getReloadStrategy() != null ? config.getReloadStrategy() : "kafka")
                    .targetServices(config.getTargetServices())
                    .changedBy(userId)
                    .changedByName(username)
                    .changedAt(LocalDateTime.now().toString())
                    .build();
            kafkaTemplate.send(CONFIG_CHANGE_TOPIC, config.getConfigKey(), JSONUtil.toJsonStr(event));
            log.info("Config change published: {} = {} (strategy={})", config.getConfigKey(), config.getConfigValue(), config.getReloadStrategy());
        } catch (Exception e) {
            log.error("Failed to publish config change: {}", config.getConfigKey(), e);
        }
    }

    // ─── 类型安全读取 ──────────────────────────────────

    @Override
    public String getStringConfig(String key, String defaultValue) {
        SystemConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        return config != null ? config.getConfigValue() : defaultValue;
    }

    @Override
    public int getIntConfig(String key, int defaultValue) {
        String value = getStringConfig(key, null);
        if (value != null) {
            try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
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

    @Override
    public double getDoubleConfig(String key, double defaultValue) {
        String value = getStringConfig(key, null);
        if (value != null) {
            try { return Double.parseDouble(value); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
