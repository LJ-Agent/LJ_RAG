package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("documents")
public class Document extends BaseEntity {

    private Long kbId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileMd5;
    private String minioPath;
    private String status;
    private String errorMessage;
    private Integer chunkCount;
    private String chunkStrategy;
    private String chunkConfig;
    private Long uploadUserId;
    private LocalDateTime uploadAt;
    private LocalDateTime completedAt;
}
