package com.rag.service.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.result.Result;
import com.rag.domain.entity.Role;
import com.rag.domain.entity.UserRole;
import com.rag.domain.mapper.RoleMapper;
import com.rag.domain.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public Result<Void> assignUserRole(Long userId, Long roleId) {
        UserRole exist = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId));
        if (exist != null) {
            return Result.success();
        }
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        userRoleMapper.insert(ur);
        return Result.success();
    }

    public Result<Void> removeUserRole(Long userId, Long roleId) {
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId));
        return Result.success();
    }

    public Result<List<Role>> getUserRoles(Long userId) {
        List<UserRole> urs = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        List<Long> roleIds = urs.stream().map(UserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return Result.success(List.of());
        }
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        return Result.success(roles);
    }
}
