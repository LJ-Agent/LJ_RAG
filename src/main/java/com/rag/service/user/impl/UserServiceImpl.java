package com.rag.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.common.util.JwtUtil;
import com.rag.domain.entity.User;
import com.rag.domain.entity.UserRole;
import com.rag.domain.mapper.PermissionMapper;
import com.rag.domain.mapper.UserMapper;
import com.rag.domain.mapper.UserRoleMapper;
import com.rag.service.user.UserService;
import com.rag.service.user.dto.LoginDTO;
import com.rag.service.user.dto.RegisterDTO;
import com.rag.service.user.dto.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionMapper permissionMapper;
    private final com.rag.domain.mapper.TeamMemberMapper teamMemberMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REFRESH_TOKEN_KEY = "user:session:%d:refresh";

    @Override
    public Result<Map<String, Object>> login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));

        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCodeEnum.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCodeEnum.PASSWORD_ERROR);
        }

        // 生成Token
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 存储refresh token到Redis
        String key = String.format(REFRESH_TOKEN_KEY, user.getId());
        stringRedisTemplate.opsForValue().set(key, refreshToken, Duration.ofDays(7));

        // 更新最后登录信息
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", 7200);
        result.put("userInfo", toVO(user));
        return Result.success(result);
    }

    @Override
    @Transactional
    public Result<Void> register(RegisterDTO dto) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException(ResultCodeEnum.USERNAME_DUPLICATE);
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setRealName(dto.getRealName());
        user.setStatus(1);
        userMapper.insert(user);

        // 默认分配VIEWER角色
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(5L); // VIEWER角色ID
        userRoleMapper.insert(userRole);

        // 默认加入默认团队(teamId=1)为访客
        try {
            com.rag.domain.entity.TeamMember tm = new com.rag.domain.entity.TeamMember();
            tm.setTeamId(1L);
            tm.setUserId(user.getId());
            tm.setRoleCode("team_viewer");
            teamMemberMapper.insert(tm);
        } catch (Exception e) {
            log.warn("新用户默认团队分配失败: {}", e.getMessage());
        }

        return Result.success();
    }

    @Override
    public Result<Map<String, Object>> refreshToken(String refreshToken) {
        if (!jwtUtil.validate(refreshToken)) {
            throw new BusinessException(ResultCodeEnum.TOKEN_INVALID);
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        String username = jwtUtil.getUsername(refreshToken);

        // 验证refresh token是否与Redis中的一致
        String key = String.format(REFRESH_TOKEN_KEY, userId);
        String storedToken = stringRedisTemplate.opsForValue().get(key);
        if (!refreshToken.equals(storedToken)) {
            throw new BusinessException(ResultCodeEnum.TOKEN_INVALID);
        }

        String newAccessToken = jwtUtil.generateAccessToken(userId, username);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);
        stringRedisTemplate.opsForValue().set(key, newRefreshToken, Duration.ofDays(7));

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("refreshToken", newRefreshToken);
        result.put("expiresIn", 7200);
        return Result.success(result);
    }

    @Override
    public Result<Void> logout(Long userId) {
        String key = String.format(REFRESH_TOKEN_KEY, userId);
        stringRedisTemplate.delete(key);
        return Result.success();
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());

        // 查询权限
        List<String> permissions = permissionMapper.selectPermissionCodesByUserId(user.getId());
        vo.setPermissions(permissions);

        return vo;
    }
}
