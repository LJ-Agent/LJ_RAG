package com.rag.service.file.dto;

import lombok.Data;

@Data
public class FileQueryDTO {

    private Long kbId;
    private String status;
    private String fileName;
    private Integer page = 1;
    private Integer size = 20;
}
