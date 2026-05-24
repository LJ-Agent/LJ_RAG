-- V4: 分块策略配置 — 新增 chunk_config 列（JSON格式存储策略参数）

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS migrate_v4()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'documents' AND COLUMN_NAME = 'chunk_config') THEN
        ALTER TABLE documents ADD COLUMN chunk_config TEXT AFTER chunk_strategy;
    END IF;
END$$

DELIMITER ;

CALL migrate_v4();
DROP PROCEDURE IF EXISTS migrate_v4;
