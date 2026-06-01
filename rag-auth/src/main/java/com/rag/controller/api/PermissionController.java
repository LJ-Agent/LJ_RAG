import org.springframework.context.annotation.Profile;
package com.rag.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.result.Result;
import com.rag.domain.entity.Permission;
import com.rag.domain.mapper.PermissionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "权限管理", description = "权限码列表 (由代码定义, 只读)")
@Profile("auth")
@Profile("auth")
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CONFIG:MANAGE')")
public class PermissionController {

    private final PermissionMapper permissionMapper;

    @Operation(summary = "全部权限码列表 (按资源类型分组)")
    @GetMapping
    public Result<Map<String, List<Permission>>> list() {
        List<Permission> all = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getResourceType, Permission::getId));
        Map<String, List<Permission>> grouped = all.stream()
                .collect(Collectors.groupingBy(Permission::getResourceType));
        return Result.success(grouped);
    }

    @Operation(summary = "权限码平铺列表")
    @GetMapping("/flat")
    public Result<List<Permission>> flat() {
        return Result.success(permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getId)));
    }
}
