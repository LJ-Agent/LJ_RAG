package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("feedback")
public class Feedback extends BaseEntity {

    private Long userId;
    private Long chatRecordId;
    private String feedbackType;
    private String content;
    private String contact;
    private String status;
    private String handlerNote;
    private Long handledBy;
    private LocalDateTime handledAt;
}
