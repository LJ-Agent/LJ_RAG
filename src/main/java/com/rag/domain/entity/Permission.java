package com.rag.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("permissions")
public class Permission extends BaseEntity {

    private String permissionName;
    private String permissionCode;
    private String resourceType;
    private String description;
}
