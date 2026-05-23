-- =============================================
-- RAG知识库系统 - 数据库初始化DDL
-- =============================================

CREATE DATABASE IF NOT EXISTS rag_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE rag_db;

-- =============================================
-- 1. 用户表
-- =============================================
CREATE TABLE users (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username        VARCHAR(64)     NOT NULL COMMENT '用户名',
    password_hash   VARCHAR(256)    NOT NULL COMMENT '密码哈希(BCrypt)',
    real_name       VARCHAR(64)     DEFAULT NULL COMMENT '真实姓名',
    email           VARCHAR(128)    DEFAULT NULL COMMENT '邮箱',
    phone           VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
    avatar_url      VARCHAR(512)    DEFAULT NULL COMMENT '头像URL',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用, 1=正常',
    last_login_at   DATETIME        DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip   VARCHAR(64)     DEFAULT NULL COMMENT '最后登录IP',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删, 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =============================================
-- 2. 角色表
-- =============================================
CREATE TABLE roles (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_name       VARCHAR(64)     NOT NULL COMMENT '角色名称',
    role_code       VARCHAR(64)     NOT NULL COMMENT '角色编码(如: ADMIN, REVIEWER)',
    description     VARCHAR(256)    DEFAULT NULL COMMENT '角色描述',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用, 1=正常',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- =============================================
-- 3. 权限表
-- =============================================
CREATE TABLE permissions (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    permission_name VARCHAR(128)    NOT NULL COMMENT '权限名称',
    permission_code VARCHAR(128)    NOT NULL COMMENT '权限编码(如: DOCUMENT:DELETE)',
    resource_type   VARCHAR(64)     NOT NULL COMMENT '资源类型(如: DOCUMENT, KB, USER)',
    description     VARCHAR(256)    DEFAULT NULL COMMENT '权限描述',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code),
    KEY idx_resource_type (resource_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- =============================================
-- 4. 用户角色关联表
-- =============================================
CREATE TABLE user_roles (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    role_id         BIGINT          NOT NULL COMMENT '角色ID',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_id (user_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- =============================================
-- 5. 角色权限关联表
-- =============================================
CREATE TABLE role_permissions (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'ID',
    role_id         BIGINT          NOT NULL COMMENT '角色ID',
    permission_id   BIGINT          NOT NULL COMMENT '权限ID',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_role_id (role_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- =============================================
-- 6. 知识库表
-- =============================================
CREATE TABLE knowledge_bases (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
    kb_name         VARCHAR(128)    NOT NULL COMMENT '知识库名称',
    description     TEXT            DEFAULT NULL COMMENT '知识库描述',
    cover_url       VARCHAR(512)    DEFAULT NULL COMMENT '封面图URL',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 0=下架, 1=上架',
    owner_id        BIGINT          NOT NULL COMMENT '创建者用户ID',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_owner_id (owner_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- =============================================
-- 7. 文档表
-- =============================================
CREATE TABLE documents (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    kb_id           BIGINT          NOT NULL COMMENT '所属知识库ID',
    file_name       VARCHAR(256)    NOT NULL COMMENT '原始文件名',
    file_type       VARCHAR(32)     NOT NULL COMMENT '文件类型(扩展名)',
    file_size       BIGINT          NOT NULL COMMENT '文件大小(字节)',
    file_md5        VARCHAR(64)     NOT NULL COMMENT '文件MD5哈希',
    minio_path      VARCHAR(512)    NOT NULL COMMENT 'MinIO存储路径',
    status          VARCHAR(32)     NOT NULL DEFAULT 'UPLOADED' COMMENT '文档状态',
    error_message   TEXT            DEFAULT NULL COMMENT '失败原因',
    chunk_count     INT             DEFAULT 0 COMMENT '分块数量',
    upload_user_id  BIGINT          NOT NULL COMMENT '上传者用户ID',
    upload_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    completed_at    DATETIME        DEFAULT NULL COMMENT '处理完成时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_md5 (file_md5),
    KEY idx_kb_id (kb_id),
    KEY idx_status (status),
    KEY idx_upload_user_id (upload_user_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

-- =============================================
-- 8. 审核记录表
-- =============================================
CREATE TABLE review_records (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    document_id     BIGINT          NOT NULL COMMENT '文档ID',
    reviewer_id     BIGINT          DEFAULT NULL COMMENT '审核人ID',
    result          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '审核结果: PENDING, APPROVED, REJECTED',
    comment         TEXT            DEFAULT NULL COMMENT '审核意见',
    reviewed_at     DATETIME        DEFAULT NULL COMMENT '审核时间',
    auto_approved   TINYINT         NOT NULL DEFAULT 0 COMMENT '是否超时自动通过: 0=否, 1=是',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_document_id (document_id),
    KEY idx_reviewer_id (reviewer_id),
    KEY idx_result (result),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核记录表';

-- =============================================
-- 9. 问答记录表
-- =============================================
CREATE TABLE chat_records (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '问答记录ID',
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    kb_ids          VARCHAR(512)    DEFAULT NULL COMMENT '查询的知识库ID列表(逗号分隔)',
    question        TEXT            NOT NULL COMMENT '用户问题',
    answer          MEDIUMTEXT      NOT NULL COMMENT '系统回答',
    source_docs     JSON            DEFAULT NULL COMMENT '引用来源(JSON)',
    rating          TINYINT         DEFAULT NULL COMMENT '用户评分: 1=差, 5=好',
    latency_ms      INT             DEFAULT NULL COMMENT '响应耗时(毫秒)',
    is_stream       TINYINT         NOT NULL DEFAULT 0 COMMENT '是否流式: 0=否, 1=是',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答记录表';

-- =============================================
-- 10. 反馈表
-- =============================================
CREATE TABLE feedback (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    chat_record_id  BIGINT          DEFAULT NULL COMMENT '关联问答记录ID',
    feedback_type   VARCHAR(32)     NOT NULL COMMENT '反馈类型: BUG, SUGGESTION, CONTENT_ERROR, OTHER',
    content         TEXT            NOT NULL COMMENT '反馈内容',
    contact         VARCHAR(128)    DEFAULT NULL COMMENT '联系方式',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING, PROCESSING, RESOLVED, CLOSED',
    handler_note    TEXT            DEFAULT NULL COMMENT '处理备注',
    handled_by      BIGINT          DEFAULT NULL COMMENT '处理人ID',
    handled_at      DATETIME        DEFAULT NULL COMMENT '处理时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_chat_record_id (chat_record_id),
    KEY idx_status (status),
    KEY idx_feedback_type (feedback_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈表';

-- =============================================
-- 11. 系统配置表
-- =============================================
CREATE TABLE system_configs (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    config_key      VARCHAR(128)    NOT NULL COMMENT '配置键',
    config_value    TEXT            NOT NULL COMMENT '配置值',
    config_type     VARCHAR(32)     NOT NULL DEFAULT 'STRING' COMMENT '配置类型: STRING, NUMBER, BOOLEAN, JSON',
    description     VARCHAR(256)    DEFAULT NULL COMMENT '配置说明',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';
