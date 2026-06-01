package com.rag.service.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewSubmitDTO {

    @NotNull(message = "文档ID不能为空")
    private Long documentId;

    @NotBlank(message = "审核结果不能为空")
    private String result;

    private String comment;
}
