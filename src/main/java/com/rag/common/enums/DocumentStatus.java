package com.rag.common.enums;

import lombok.Getter;

import java.util.Set;

/**
 * 文档状态枚举，内置状态转移规则。
 */
@Getter
public enum DocumentStatus {

    UPLOADED("已上传"),
    PARSING("解析中"),
    CLEANING("清洗中"),
    PENDING_REVIEW("待审核"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    CHUNKING("分块中"),
    EMBEDDING("向量化中"),
    COMPLETED("已完成"),
    PARSING_FAILED("解析失败"),
    CLEANING_FAILED("清洗失败"),
    CHUNKING_FAILED("分块失败"),
    EMBEDDING_FAILED("向量化失败");

    private final String description;

    private Set<DocumentStatus> nextStates;

    DocumentStatus(String description) {
        this.description = description;
    }

    static {
        UPLOADED.nextStates = Set.of(PARSING);
        PARSING.nextStates = Set.of(CLEANING, PARSING_FAILED);
        CLEANING.nextStates = Set.of(PENDING_REVIEW, CLEANING_FAILED);
        PENDING_REVIEW.nextStates = Set.of(APPROVED, REJECTED);
        APPROVED.nextStates = Set.of(CHUNKING);
        REJECTED.nextStates = Set.of(PARSING);
        CHUNKING.nextStates = Set.of(EMBEDDING, CHUNKING_FAILED);
        EMBEDDING.nextStates = Set.of(COMPLETED, EMBEDDING_FAILED);
        PARSING_FAILED.nextStates = Set.of(PARSING);
        CLEANING_FAILED.nextStates = Set.of(CLEANING);
        CHUNKING_FAILED.nextStates = Set.of(CHUNKING);
        EMBEDDING_FAILED.nextStates = Set.of(EMBEDDING);
        COMPLETED.nextStates = Set.of();
    }

    public boolean canTransitTo(DocumentStatus target) {
        return nextStates != null && nextStates.contains(target);
    }

    public boolean isFinal() {
        return nextStates == null || nextStates.isEmpty();
    }
}
