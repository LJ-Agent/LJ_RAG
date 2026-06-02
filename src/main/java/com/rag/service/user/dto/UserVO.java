package com.rag.service.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String avatarUrl;
    private Integer status;
    private List<String> roles;
    private List<String> permissions;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
