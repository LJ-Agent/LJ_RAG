package com.rag.service.file.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FileUploadDTO {

    /** 目标知识库ID */
    @NotNull(message = "知识库ID不能为空")
    private Long kbId;
}
