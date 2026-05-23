package com.rag.common.enums;

import lombok.Getter;

@Getter
public enum ReviewResult {

    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已驳回");

    private final String description;

    ReviewResult(String description) {
        this.description = description;
    }
}
