package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_config_histories")
public class ConfigHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long configId;
    private String configKey;
    private String oldValue;
    private String newValue;
    private Long changedBy;
    private String changedByName;
    private LocalDateTime changedAt;
}
