package com.rag.service.qa.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVO {

    private Long id;
    private Long userId;
    private String title;
    private String kbIds;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
