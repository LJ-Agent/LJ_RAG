package com.rag.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.context.UserContext;
import com.rag.common.result.Result;
import com.rag.domain.entity.UserConfig;
import com.rag.domain.mapper.UserConfigMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "个人配置", description = "用户个人偏好配置 (每个用户独立)")
@RestController
@RequestMapping("/api/user/configs")
@RequiredArgsConstructor
public class UserConfigController {

    private final UserConfigMapper userConfigMapper;

    @Operation(summary = "获取当前用户的所有个人配置")
    @GetMapping
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
