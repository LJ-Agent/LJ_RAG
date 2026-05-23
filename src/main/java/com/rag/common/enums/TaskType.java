package com.rag.common.enums;

import lombok.Getter;

@Getter
public enum TaskType {

    FILE_PROCESS("文件处理"),
    CHUNK_PROCESS("分块向量化");

    private final String description;

    TaskType(String description) {
        this.description = description;
    }
}
