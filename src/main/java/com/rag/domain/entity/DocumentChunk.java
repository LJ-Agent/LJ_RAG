package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("document_chunks")
public class DocumentChunk implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private String chunkId;
    private Integer chunkIndex;
    private String content;
    private Integer level;
    private String parentId;
    private Integer charCount;
    private String status;
    private LocalDateTime createdAt;
}
