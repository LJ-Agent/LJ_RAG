package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_configs")
public class SystemConfig extends BaseEntity {

    private String configKey;
    private String configValue;
    private String configType;
    private String description;
    private String category;
    private String label;
    @TableField("default_value")
    private String defaultVal;
    private String validationRule;
    private Integer sortOrder;
    private Integer editable;
    private String targetServices;
    private String reloadStrategy;
    private String status;
}
