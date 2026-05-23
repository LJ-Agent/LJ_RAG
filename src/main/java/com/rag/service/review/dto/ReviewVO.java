package com.rag.service.review.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewVO {

    private Long id;
    private Long documentId;
    private String documentName;
    private Long reviewerId;
    private String reviewerName;
    private String result;
    private String comment;
    private Integer autoApproved;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
