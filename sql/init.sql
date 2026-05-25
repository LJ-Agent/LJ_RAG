-- RAG-MEMORY MySQL Schema
-- This script is idempotent (IF NOT EXISTS)

-- 1. User memory profiles
CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_facts INT NOT NULL DEFAULT 0,
    total_episodes INT NOT NULL DEFAULT 0,
    total_rules INT NOT NULL DEFAULT 0,
    working_entry_count INT NOT NULL DEFAULT 0,
    last_active_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Atomic facts
CREATE TABLE IF NOT EXISTS atomic_facts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fact_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) DEFAULT NULL,
    content TEXT NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'factual',
    importance DOUBLE NOT NULL DEFAULT 0.5,
    tags JSON DEFAULT NULL,
    source_docs JSON DEFAULT NULL,
    access_count INT NOT NULL DEFAULT 0,
    created_at_ms BIGINT NOT NULL,
    accessed_at_ms BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fact_id (fact_id),
    INDEX idx_user_category (user_id, category),
    INDEX idx_user_importance (user_id, importance),
    INDEX idx_user_created (user_id, created_at_ms),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Episodic summaries
CREATE TABLE IF NOT EXISTS episodic_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) DEFAULT NULL,
    summary TEXT NOT NULL,
    period VARCHAR(16) NOT NULL DEFAULT 'session',
    key_fact_ids JSON DEFAULT NULL,
    start_time_ms BIGINT NOT NULL,
    end_time_ms BIGINT NOT NULL,
    created_at_ms BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_episode_id (episode_id),
    INDEX idx_user_period (user_id, period),
    INDEX idx_user_end_time (user_id, end_time_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Procedural rules
CREATE TABLE IF NOT EXISTS procedural_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    rule_content TEXT NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'preference',
    supporting_fact_ids JSON DEFAULT NULL,
    confidence DOUBLE NOT NULL DEFAULT 0.5,
    activation_count INT NOT NULL DEFAULT 0,
    created_at_ms BIGINT NOT NULL,
    last_activated_ms BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rule_id (rule_id),
    INDEX idx_user_category (user_id, category),
    INDEX idx_user_confidence (user_id, confidence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Working memory archive
CREATE TABLE IF NOT EXISTS working_memory_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entry_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    entry_key VARCHAR(255) NOT NULL,
    entry_value TEXT DEFAULT NULL,
    created_at_ms BIGINT NOT NULL,
    archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_session (user_id, session_id),
    INDEX idx_archived_at (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Audit logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) DEFAULT NULL,
    detail JSON DEFAULT NULL,
    operator_ip VARCHAR(45) DEFAULT NULL,
    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, operation_time),
    INDEX idx_operation (operation, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Memory snapshots
CREATE TABLE IF NOT EXISTS memory_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_data JSON NOT NULL,
    fact_count INT NOT NULL DEFAULT 0,
    episode_count INT NOT NULL DEFAULT 0,
    rule_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
