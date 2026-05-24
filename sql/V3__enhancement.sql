-- V3: 功能增强 - 分块策略配置、会话管理、分块数据持久化
-- 先执行 ALTER TABLE，若列已存在则忽略（MySQL 8.0+ 不支持 IF NOT EXISTS for columns，需要 PL/SQL）

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS migrate_v3()
BEGIN
    -- 1. 分块策略字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'documents' AND COLUMN_NAME = 'chunk_strategy') THEN
        ALTER TABLE documents ADD COLUMN chunk_strategy VARCHAR(32) DEFAULT 'semantic' AFTER chunk_count;
    END IF;

    -- 2. 聊天会话表
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_sessions') THEN
        CREATE TABLE chat_sessions (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id BIGINT NOT NULL,
            title VARCHAR(255) DEFAULT '新会话',
            kb_ids VARCHAR(500),
            message_count INT DEFAULT 0,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            deleted TINYINT DEFAULT 0,
            INDEX idx_user_id (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    -- 3. 问答记录会话ID
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_records' AND COLUMN_NAME = 'session_id') THEN
        ALTER TABLE chat_records ADD COLUMN session_id BIGINT AFTER user_id;
    END IF;

    -- 4. 文档分块表
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'document_chunks') THEN
        CREATE TABLE document_chunks (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            document_id BIGINT NOT NULL,
            chunk_id VARCHAR(64) NOT NULL,
            chunk_index INT NOT NULL,
            content TEXT NOT NULL,
            level INT DEFAULT 0,
            parent_id VARCHAR(64),
            char_count INT DEFAULT 0,
            status VARCHAR(20) DEFAULT 'ACTIVE',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_document_id (document_id),
            INDEX idx_chunk_id (chunk_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;
END$$

DELIMITER ;

CALL migrate_v3();
DROP PROCEDURE IF EXISTS migrate_v3;
