package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("chat_sessions")
public class ChatSession implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String kbIds;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
