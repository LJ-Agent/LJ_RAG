package com.rag.service.permission.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.domain.entity.Permission;
import com.rag.domain.entity.Role;
import com.rag.domain.entity.RolePermission;
import com.rag.domain.entity.UserRole;
import com.rag.domain.mapper.PermissionMapper;
import com.rag.domain.mapper.RoleMapper;
import com.rag.domain.mapper.RolePermissionMapper;
import com.rag.domain.mapper.UserRoleMapper;
import com.rag.service.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public Result<List<Role>> listRoles() {
        return Result.success(roleMapper.selectList(null));
    }

    @Override
    @Transactional
    public Result<Role> createRole(Role role) {
        roleMapper.insert(role);
        return Result.success(role);
    }

    @Override
    @Transactional
    public Result<Void> deleteRole(Long roleId) {
        // 删除角色关联的权限
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
        // 删除用户的该角色
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, roleId));
        roleMapper.deleteById(roleId);
        return Result.success();
    }

    @Override
    public Result<List<Permission>> listPermissions() {
        return Result.success(permissionMapper.selectList(null));
    }

    @Override
    @Transactional
    public Result<Void> assignPermission(Long roleId, Long permissionId) {
        Long count = rolePermissionMapper.selectCount(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId)
                        .eq(RolePermission::getPermissionId, permissionId));
        if (count > 0) {
            return Result.success(); // 已存在
        }
        RolePermission rp = new RolePermission();
        rp.setRoleId(roleId);
        rp.setPermissionId(permissionId);
        rolePermissionMapper.insert(rp);
        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> removePermission(Long roleId, Long permissionId) {
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId)
                        .eq(RolePermission::getPermissionId, permissionId));
        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> assignUserRole(Long userId, Long roleId) {
        Long count = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId));
        if (count > 0) {
            return Result.success();
        }
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        userRoleMapper.insert(ur);
        return Result.success();
    }

    @Override
    @Transactional
    public Result<Void> removeUserRole(Long userId, Long roleId) {
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId));
        return Result.success();
    }

    @Override
    public Result<List<Role>> getUserRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return Result.success(List.of());
        }
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        return Result.success(roles);
    }
}
