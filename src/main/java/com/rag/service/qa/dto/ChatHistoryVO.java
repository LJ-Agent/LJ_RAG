package com.rag.service.qa.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatHistoryVO {

    private Long id;
    private Long sessionId;
    private String question;
    private String answer;
    private Integer rating;
    private Integer latencyMs;
    private Integer isStream;
    private LocalDateTime createdAt;
}
