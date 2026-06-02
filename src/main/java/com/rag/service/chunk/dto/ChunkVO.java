package com.rag.service.chunk.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChunkVO {

    private Long id;
    private Long documentId;
    private String chunkId;
    private Integer chunkIndex;
    private String content;
    private Integer level;
    private String parentId;
    private Integer charCount;
    private String status;
    private LocalDateTime createdAt;

    @Data
    public static class ChunkStats {
        private int totalCount;
        private int activeCount;
        private int deletedCount;
        private long totalChars;
    }
}
