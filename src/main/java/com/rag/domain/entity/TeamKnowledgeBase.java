package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("team_knowledge_bases")
public class TeamKnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long kbId;
}
