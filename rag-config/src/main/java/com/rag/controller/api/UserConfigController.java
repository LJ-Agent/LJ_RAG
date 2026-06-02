import org.springframework.context.annotation.Profile;
package com.rag.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.context.UserContext;
import com.rag.common.result.Result;
import com.rag.domain.entity.SystemConfig;
import com.rag.domain.entity.UserConfig;
import com.rag.domain.mapper.SystemConfigMapper;
import com.rag.domain.mapper.UserConfigMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "个人配置", description = "用户个人偏好配置 (每个用户独立)")
@Profile("config")
@Profile("config")
@RestController
@RequestMapping("/api/user/configs")
@RequiredArgsConstructor
public class UserConfigController {

    private final UserConfigMapper userConfigMapper;
    private final com.rag.domain.mapper.SystemConfigMapper systemConfigMapper;

    @Operation(summary = "获取当前用户的有效配置 (个人偏好 > 系统默认)")
    @PostMapping("/effective")
    public Result<List<Map<String, Object>>> effective() {
        Long userId = UserContext.getUserId();
        // 系统所有 personal 模板
        List<SystemConfig> templates = systemConfigMapper.selectList(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getScope, "personal"));
        // 用户个人配置
        List<UserConfig> userConfigs = userConfigMapper.selectList(
                new LambdaQueryWrapper<UserConfig>().eq(UserConfig::getUserId, userId));
        // 合并: 用户值 > 系统默认
        List<Map<String, Object>> result = new ArrayList<>();
        for (SystemConfig t : templates) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("configKey", t.getConfigKey());
            item.put("label", t.getLabel() != null ? t.getLabel() : t.getConfigKey());
            item.put("configType", t.getConfigType());
            item.put("description", t.getDescription());
            item.put("defaultValue", t.getConfigValue());
            item.put("validationRule", t.getValidationRule());
            item.put("category", t.getCategory());
            // 查找用户覆盖值
            UserConfig uc = userConfigs.stream()
                    .filter(u -> u.getConfigKey().equals(t.getConfigKey()))
                    .findFirst().orElse(null);
            if (uc != null) {
                item.put("configValue", uc.getConfigValue());
                item.put("fromUser", true);
                item.put("userConfigId", uc.getId());
            } else {
                item.put("configValue", t.getConfigValue());
                item.put("fromUser", false);
                item.put("userConfigId", 0);
            }
            result.add(item);
        }
        return Result.success(result);
    }

    @Operation(summary = "获取当前用户的所有个人配置")
    @PostMapping("/list")
    public Result<List<UserConfig>> list() {
        Long userId = UserContext.getUserId();
        return Result.success(userConfigMapper.selectList(
                new LambdaQueryWrapper<UserConfig>().eq(UserConfig::getUserId, userId)));
    }

    @Operation(summary = "保存或更新个人配置")
    @PostMapping
    public Result<UserConfig> save(@RequestBody UserConfig config) {
        Long userId = UserContext.getUserId();
        // 查找已有
        UserConfig exist = userConfigMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>()
                        .eq(UserConfig::getUserId, userId)
                        .eq(UserConfig::getConfigKey, config.getConfigKey()));
        if (exist != null) {
            exist.setConfigValue(config.getConfigValue());
            if (config.getDescription() != null) exist.setDescription(config.getDescription());
            userConfigMapper.updateById(exist);
            return Result.success(exist);
        }
        config.setUserId(userId);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        userConfigMapper.insert(config);
        return Result.success(config);
    }

    @Operation(summary = "删除个人配置 (恢复系统默认)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        UserConfig config = userConfigMapper.selectById(id);
        if (config != null && config.getUserId().equals(userId)) {
            userConfigMapper.deleteById(id);
        }
        return Result.success();
    }
}
