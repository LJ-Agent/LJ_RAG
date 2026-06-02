package com.rag.infrastructure.security;

import java.lang.annotation.*;

/**
 * 团队权限校验注解 — 标注在 Controller 方法上。
 *
 * 自动从请求参数中提取 kbId/documentId → 查询所属团队 → 校验当前用户角色。
 *
 * 用法：@TeamPermission(value = "DOCUMENT:UPLOAD", resourceType = ResourceType.KB)
 *       @TeamPermission(value = "DOCUMENT:VIEW",   resourceType = ResourceType.DOCUMENT)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TeamPermission {
    /** 所需权限码，如 DOCUMENT:UPLOAD, DOCUMENT:VIEW, KB:DELETE */
    String value();

    /** 资源类型：KB（知识库级）或 DOCUMENT（文档级） */
    ResourceType resourceType() default ResourceType.KB;

    /** 请求参数中 kbId 的字段名（默认 kbId） */
    String kbIdParam() default "kbId";

    /** 请求参数中文档ID的字段名（默认 id，用于路径变量 @PathVariable Long id） */
    String docIdParam() default "id";

    enum ResourceType {
        /** 知识库级别的操作：参数中有 kbId */
        KB,
        /** 文档级别的操作：参数中有 documentId，需通过文档找到归属 KB */
        DOCUMENT
    }
}
