package com.rag.service.user;

import com.rag.common.result.Result;
import com.rag.domain.entity.User;
import com.rag.service.user.dto.LoginDTO;
import com.rag.service.user.dto.RegisterDTO;
import com.rag.service.user.dto.UserVO;

import java.util.Map;

public interface UserService {

    Result<Map<String, Object>> login(LoginDTO dto);

    Result<Void> register(RegisterDTO dto);

    Result<Map<String, Object>> refreshToken(String refreshToken);

    Result<Void> logout(Long userId);

    UserVO getCurrentUser(Long userId);

    User getById(Long userId);
}
