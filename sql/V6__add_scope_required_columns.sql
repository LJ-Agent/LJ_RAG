-- V6: 添加 system_configs 缺失的 scope 和 required 列
-- 修复系统配置页面空白问题：listByCategory/effective 按 scope 查询，但表中无此列

ALTER TABLE system_configs
    ADD COLUMN scope    VARCHAR(32)  DEFAULT 'system' COMMENT '作用范围: system=全局系统配置, personal=个人偏好模板, tenant=租户级',
    ADD COLUMN required TINYINT      DEFAULT 0       COMMENT '是否关键配置: 1=不可删除, 0=可删除';

-- 已有数据统一设为系统级配置
UPDATE system_configs SET scope = 'system' WHERE scope IS NULL OR scope = '';

-- 标记关键配置（代码逻辑硬依赖，不可删除）
UPDATE system_configs SET required = 1 WHERE config_key IN (
    'chunk.default_size',
    'chunk.strategy',
    'retrieval.default_top_k',
    'retrieval.vector_weight',
    'retrieval.bm25_weight',
    'qa.cache_enabled',
    'qa.rate_limit_per_second',
    'rate_limit.default_per_second',
    'review.auto_approve_hours',
    'review.global_review_enabled',
    'upload.max_file_size_mb'
);
