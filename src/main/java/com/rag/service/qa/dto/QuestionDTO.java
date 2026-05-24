package com.rag.service.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class QuestionDTO {

    @NotBlank(message = "问题不能为空")
    private String question;

    @NotNull(message = "知识库ID不能为空")
    private List<Long> kbIds;

    private Long sessionId;
    private Integer topK = 5;
    private Float scoreThreshold = 0.7f;
}
