package com.rag.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.domain.entity.SystemConfig;
import com.rag.service.config.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统配置", description = "系统参数可视化管理")
@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    @Operation(summary = "配置列表")
    @GetMapping
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Page<SystemConfig>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "50") Integer size) {
        return configService.list(page, size);
    }

    @Operation(summary = "根据Key获取配置")
    @GetMapping("/{key}")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<SystemConfig> getByKey(@PathVariable String key) {
        return configService.getByKey(key);
    }

    @Operation(summary = "保存或更新配置")
    @PostMapping
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Void> save(@RequestBody SystemConfig config) {
        return configService.saveOrUpdate(config);
    }

    @Operation(summary = "更新配置")
    @PutMapping
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Void> update(@RequestBody SystemConfig config) {
        return configService.saveOrUpdate(config);
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CONFIG:MANAGE')")
    public Result<Void> delete(@PathVariable Long id) {
        return configService.delete(id);
    }
}
