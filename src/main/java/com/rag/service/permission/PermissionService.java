package com.rag.service.permission;

import com.rag.common.result.Result;
import com.rag.domain.entity.Permission;
import com.rag.domain.entity.Role;

import java.util.List;

public interface PermissionService {

    // 角色管理
    Result<List<Role>> listRoles();

    Result<Role> createRole(Role role);

    Result<Void> deleteRole(Long roleId);

    // 权限查询
    Result<List<Permission>> listPermissions();

    // 角色权限分配
    Result<Void> assignPermission(Long roleId, Long permissionId);

    Result<Void> removePermission(Long roleId, Long permissionId);

    // 用户角色分配
    Result<Void> assignUserRole(Long userId, Long roleId);

    Result<Void> removeUserRole(Long userId, Long roleId);

    Result<List<Role>> getUserRoles(Long userId);
}
