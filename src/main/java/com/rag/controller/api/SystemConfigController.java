package com.rag.controller.api;
import org.springframework.context.annotation.Profile;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.context.UserContext;
import com.rag.common.result.Result;
import com.rag.domain.entity.ConfigHistory;
import com.rag.domain.entity.SystemConfig;
import com.rag.service.config.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "系统配置", description = "系统参数可视化管理 — 支持分类浏览、校验、批量更新、历史追溯、回滚")
@Profile({"config", "prod"})
@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    @Operation(summary = "系统配置列表（支持按分类/scope筛选）")
    @GetMapping
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Page<SystemConfig>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "50") Integer size,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String scope) {
        return configService.list(page, size, category, scope);
    }

    @Operation(summary = "按分类获取配置列表")
    @GetMapping("/category/{category}")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<List<SystemConfig>> listByCategory(@PathVariable String category) {
        return configService.listByCategory(category);
    }

    @Operation(summary = "获取配置分类列表")
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<List<String>> categories() {
        return Result.success(List.of("chunk", "retrieval", "cleaning", "qa", "rate_limit", "review", "upload", "general"));
    }

    @Operation(summary = "根据Key获取配置详情")
    @GetMapping("/{key}")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<SystemConfig> getByKey(@PathVariable String key) {
        return configService.getByKey(key);
    }

    @Operation(summary = "保存或更新配置（含校验+历史+Kafka发布）")
    @PostMapping
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Map<String, Object>> saveOrUpdate(@RequestBody SystemConfig config) {
        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        return configService.saveOrUpdateConfig(config, userId, username);
    }

    @Operation(summary = "批量更新配置")
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Map<String, Object>> batchUpdate(@RequestBody List<SystemConfig> configs) {
        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        return configService.batchUpdate(configs, userId, username);
    }

    @Operation(summary = "校验配置值（不保存）")
    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Map<String, String>> validate(@RequestBody SystemConfig config) {
        String error = configService.validateConfigValue(config);
        if (error != null) {
            return Result.success(Map.of("valid", "false", "error", error));
        }
        return Result.success(Map.of("valid", "true", "error", ""));
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Void> delete(@PathVariable Long id) {
        return configService.delete(id);
    }

    @Operation(summary = "获取配置变更历史")
    @GetMapping("/{key}/history")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<List<ConfigHistory>> getHistory(@PathVariable String key) {
        return configService.getHistory(key);
    }

    @Operation(summary = "回滚配置到历史版本")
    @PostMapping("/{key}/rollback/{historyId}")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Map<String, Object>> rollback(@PathVariable String key, @PathVariable Long historyId) {
        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        return configService.rollback(key, historyId, userId, username);
    }
}
