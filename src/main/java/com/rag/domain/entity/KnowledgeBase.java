package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_bases")
public class KnowledgeBase extends BaseEntity {

    private String kbName;
    private String description;
    private String coverUrl;
    private Integer status;
    private Long ownerId;
    private Long teamId;
    private String visibility;
}
