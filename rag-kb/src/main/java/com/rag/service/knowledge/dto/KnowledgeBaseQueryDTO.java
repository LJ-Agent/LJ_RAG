package com.rag.service.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeBaseQueryDTO {

    private String kbName;
    private Integer status;
    private Integer page = 1;
    private Integer size = 20;
}
