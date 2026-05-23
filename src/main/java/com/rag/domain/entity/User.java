package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class User extends BaseEntity {

    private String username;
    private String passwordHash;
    private String realName;
    private String email;
    private String phone;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
}
