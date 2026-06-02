import org.springframework.context.annotation.Profile;
package com.rag.controller.api;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.common.exception.BusinessException;
import com.rag.domain.entity.Role;
import com.rag.domain.entity.RolePermission;
import com.rag.domain.mapper.RoleMapper;
import com.rag.domain.mapper.RolePermissionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "角色管理", description = "角色CRUD + 权限分配 — 灵活可配置的RBAC")
@Profile("auth")
@Profile("auth")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('CONFIG:MANAGE','ROLE:MANAGE')")
public class RoleController {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rpMapper;

    @Operation(summary = "角色列表")
    @GetMapping
    public Result<Page<Role>> list(@RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "50") Integer size) {
        Page<Role> pg = new Page<>(page, size);
        return Result.success(roleMapper.selectPage(pg,
                new LambdaQueryWrapper<Role>().orderByAsc(Role::getId)));
    }

    @Operation(summary = "角色详情(含权限列表)")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "角色不存在");
        // 查询该角色拥有的权限ID列表
        List<Long> permIds = rpMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id))
                .stream().map(RolePermission::getPermissionId).toList();
        return Result.success(Map.of("role", role, "permissionIds", permIds));
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @Transactional
    public Result<Role> create(@RequestBody Role role) {
        Role exist = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, role.getRoleCode()));
        if (exist != null) throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "角色编码已存在");
        roleMapper.insert(role);
        return Result.success(role);
    }

    @Operation(summary = "编辑角色基本信息")
    @PutMapping("/{id}")
    public Result<Role> update(@PathVariable Long id, @RequestBody Role role) {
        Role exist = roleMapper.selectById(id);
        if (exist == null) throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "角色不存在");
        if (role.getRoleName() != null) exist.setRoleName(role.getRoleName());
        if (role.getDescription() != null) exist.setDescription(role.getDescription());
        if (role.getStatus() != null) exist.setStatus(role.getStatus());
        // role_code 不可修改
        roleMapper.updateById(exist);
        return Result.success(exist);
    }

    @Operation(summary = "分配权限 — 传入 permissionIds 数组，全量替换")
    @PutMapping("/{id}/permissions")
    @Transactional
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> permIds = body.get("permissionIds");
        if (permIds == null) throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "请提供permissionIds");
        // 先删后插
        rpMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        for (Long pid : permIds) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(id); rp.setPermissionId(pid);
            rpMapper.insert(rp);
        }
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        rpMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        roleMapper.deleteById(id);
        return Result.success();
    }
}
