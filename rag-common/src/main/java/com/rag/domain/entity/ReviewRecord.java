package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_records")
public class ReviewRecord extends BaseEntity {

    private Long documentId;
    private Long reviewerId;
    private String result;
    private String comment;
    private LocalDateTime reviewedAt;
    private Integer autoApproved;
}
