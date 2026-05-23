package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("chat_records")
public class ChatRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String kbIds;
    private String question;
    private String answer;
    private String sourceDocs;
    private Integer rating;
    private Integer latencyMs;
    private Integer isStream;
    private LocalDateTime createdAt;
}
