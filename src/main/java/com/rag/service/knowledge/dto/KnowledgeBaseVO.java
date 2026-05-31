package com.rag.service.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseVO {

    private Long id;
    private String kbName;
    private String description;
    private String coverUrl;
    private Integer status;
    private Long ownerId;
    private String ownerName;
    private Long teamId;
    private String teamName;
    private Long documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
