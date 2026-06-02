import org.springframework.context.annotation.Profile;
package com.rag.controller.api;

import com.rag.common.result.Result;
import com.rag.controller.interceptor.JwtAuthInterceptor;
import com.rag.service.user.UserService;
import com.rag.service.user.dto.LoginDTO;
import com.rag.service.user.dto.RegisterDTO;
import com.rag.service.user.dto.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "认证管理", description = "用户登录、注册、Token刷新")
@Profile("auth")
@Profile("auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return userService.login(dto);
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        return userService.register(dto);
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestParam String refreshToken) {
        return userService.refreshToken(refreshToken);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return userService.logout(userId);
    }

    @Operation(summary = "获取当前用户信息")
    @PostMapping("/me")
    public Result<UserVO> me() {
        Long userId = JwtAuthInterceptor.CURRENT_USER_ID.get();
        return Result.success(userService.getCurrentUser(userId));
    }
}
