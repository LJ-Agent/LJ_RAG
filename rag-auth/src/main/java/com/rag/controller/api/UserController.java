import org.springframework.context.annotation.Profile;
package com.rag.controller.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.result.Result;
import com.rag.controller.interceptor.JwtAuthInterceptor;
import com.rag.domain.entity.Role;
import com.rag.domain.entity.User;
import com.rag.domain.mapper.UserMapper;
import com.rag.service.permission.PermissionService;
import com.rag.service.user.UserService;
import com.rag.service.user.dto.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理", description = "用户信息管理、角色分配")
@Profile("auth")
@Profile("auth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "获取当前用户信息")
    @PostMapping("/me")
    public Result<UserVO> me() {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return Result.success(userService.getCurrentUser(userId));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        User user = userMapper.selectById(userId);
        if (!passwordEncoder.matches(body.get("oldPassword"), user.getPasswordHash())) {
            return Result.fail(401, "原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(body.get("newPassword")));
        userMapper.updateById(user);
        return Result.success();
    }

    @Operation(summary = "用户列表")
    @PostMapping
    @PreAuthorize("hasAuthority('USER:VIEW')")
    public Result<Page<UserVO>> list(@RequestBody(required = false) Map<String, Object> body) {
        int page = body != null && body.containsKey("page") ? ((Number) body.get("page")).intValue() : 1;
        int size = body != null && body.containsKey("size") ? ((Number) body.get("size")).intValue() : 20;
        Page<User> pg = new Page<>(page, size);
        Page<User> result = userMapper.selectPage(pg, null);
        Page<UserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(u -> userService.getCurrentUser(u.getId())).toList());
        return Result.success(voPage);
    }

    @Operation(summary = "分配用户角色")
    @PostMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('USER:UPDATE')")
    public Result<Void> assignRole(@PathVariable Long userId, @PathVariable Long roleId) {
        return permissionService.assignUserRole(userId, roleId);
    }

    @Operation(summary = "移除用户角色")
    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('USER:UPDATE')")
    public Result<Void> removeRole(@PathVariable Long userId, @PathVariable Long roleId) {
        return permissionService.removeUserRole(userId, roleId);
    }

    @Operation(summary = "查询用户角色")
    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('USER:VIEW')")
    public Result<List<Role>> getUserRoles(@PathVariable Long userId) {
        return permissionService.getUserRoles(userId);
    }
}
