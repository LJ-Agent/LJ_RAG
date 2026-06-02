package com.rag.common.enums;

import lombok.Getter;

@Getter
public enum UserStatus {

    DISABLED(0, "禁用"),
    ENABLED(1, "正常");

    private final int code;
    private final String description;

    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
