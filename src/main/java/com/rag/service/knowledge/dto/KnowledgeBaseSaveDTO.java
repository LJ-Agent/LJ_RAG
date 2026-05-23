package com.rag.service.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeBaseSaveDTO {

    @NotBlank(message = "知识库名称不能为空")
    private String kbName;

    private String description;
    private String coverUrl;
    private Integer status;
}
