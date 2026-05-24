package com.rag.service.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileVO {

    private Long id;
    private Long kbId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileMd5;
    private String status;
    private String errorMessage;
    private Integer chunkCount;
    private String chunkStrategy;
    private Long uploadUserId;
    private LocalDateTime uploadAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
