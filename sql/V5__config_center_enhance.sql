-- V5: 配置中心增强 — 添加分类、校验规则、生效策略等字段 + 变更历史表
-- 使系统配置支持可视化编辑、前后端校验、Kafka热生效、版本追溯

ALTER TABLE system_configs
    ADD COLUMN IF NOT EXISTS category       VARCHAR(64)   DEFAULT 'general' COMMENT '分类: chunk/retrieval/cleaning/llm/qa/rate_limit/review/auth/upload',
    ADD COLUMN IF NOT EXISTS label           VARCHAR(128)  COMMENT '中文显示名',
    ADD COLUMN IF NOT EXISTS default_value   VARCHAR(512)  COMMENT '出厂默认值',
    ADD COLUMN IF NOT EXISTS validation_rule VARCHAR(512)  COMMENT '校验规则JSON: {type,min,max,enum,pattern}',
    ADD COLUMN IF NOT EXISTS sort_order      INT           DEFAULT 0 COMMENT '排序',
    ADD COLUMN IF NOT EXISTS editable        TINYINT       DEFAULT 1 COMMENT '是否允许页面编辑',
    ADD COLUMN IF NOT EXISTS target_services VARCHAR(256)  COMMENT '影响的服务(逗号分隔): rag-python,rag-cleaning',
    ADD COLUMN IF NOT EXISTS reload_strategy VARCHAR(32)   DEFAULT 'kafka' COMMENT '生效策略: kafka/api/restart',
    ADD COLUMN IF NOT EXISTS status          VARCHAR(16)   DEFAULT 'active' COMMENT '状态: active/disabled';

-- 更新已有配置的分类和标签
UPDATE system_configs SET category = 'review',     label = '自动审核小时数',     reload_strategy = 'api'  WHERE config_key = 'review.auto_approve_hours';
UPDATE system_configs SET category = 'review',     label = '全局审核开关',       reload_strategy = 'api'  WHERE config_key = 'review.global_review_enabled';
UPDATE system_configs SET category = 'qa',         label = '问答缓存开关',       reload_strategy = 'api'  WHERE config_key = 'qa.cache_enabled';
UPDATE system_configs SET category = 'qa',         label = '问答缓存有效期(秒)', reload_strategy = 'kafka' WHERE config_key = 'qa.cache_ttl_seconds';
UPDATE system_configs SET category = 'rate_limit', label = '问答每秒请求限制',    reload_strategy = 'api'  WHERE config_key = 'qa.rate_limit_per_second';

-- 插入分块策略默认配置
INSERT IGNORE INTO system_configs (config_key, config_value, config_type, description, label, default_value, validation_rule, category, sort_order, target_services, reload_strategy) VALUES
('chunk.default_size', '500', 'NUMBER', '默认分块目标大小(字符)', '默认分块大小', '500', '{"type":"int","min":100,"max":60000}', 'chunk', 1, 'rag-python', 'api'),
('chunk.overlap', '50', 'NUMBER', '块重叠大小(字符)', '块重叠大小', '50', '{"type":"int","min":0,"max":5000}', 'chunk', 2, 'rag-python', 'api'),
('chunk.min_chunk_size', '100', 'NUMBER', '最小有效分块大小(字符)', '最小分块大小', '100', '{"type":"int","min":10,"max":10000}', 'chunk', 3, 'rag-python', 'api'),
('chunk.max_chunk_size', '30000', 'NUMBER', '最大分块大小(字符), 受Milvus VarChar 65535限制', '最大分块大小', '30000', '{"type":"int","min":500,"max":60000}', 'chunk', 4, 'rag-python', 'api'),
('chunk.strategy', 'semantic', 'STRING', '默认分块策略', '默认分块策略', 'semantic', '{"type":"enum","values":["fixed","hierarchical","semantic","recursive","topic","hybrid"]}', 'chunk', 5, 'rag-python', 'api'),

-- 检索参数
('retrieval.default_top_k', '5', 'NUMBER', '默认返回片段数', '默认TopK', '5', '{"type":"int","min":1,"max":50}', 'retrieval', 1, 'rag-python,rag-que', 'kafka'),
('retrieval.max_top_k', '20', 'NUMBER', '最大返回片段数', '最大TopK', '20', '{"type":"int","min":1,"max":100}', 'retrieval', 2, 'rag-python,rag-que', 'kafka'),
('retrieval.score_threshold', '0.3', 'NUMBER', '检索分数阈值[0,1]', '分数阈值', '0.3', '{"type":"float","min":0.0,"max":1.0}', 'retrieval', 3, 'rag-python', 'kafka'),
('retrieval.vector_weight', '0.7', 'NUMBER', '混合检索向量权重', '向量权重', '0.7', '{"type":"float","min":0.0,"max":1.0}', 'retrieval', 4, 'rag-python', 'kafka'),
('retrieval.bm25_weight', '0.3', 'NUMBER', '混合检索BM25权重', 'BM25权重', '0.3', '{"type":"float","min":0.0,"max":1.0}', 'retrieval', 5, 'rag-python', 'kafka'),

-- 清洗参数
('cleaning.ocr_enabled', 'true', 'BOOLEAN', 'PDF扫描件OCR自动识别', 'OCR自动识别', 'true', '{"type":"bool"}', 'cleaning', 1, 'rag-cleaning', 'api'),
('cleaning.sensitive_mask_enabled', 'true', 'BOOLEAN', '敏感信息脱敏开关', '脱敏开关', 'true', '{"type":"bool"}', 'cleaning', 2, 'rag-cleaning', 'api'),
('cleaning.quality_threshold', '0.6', 'NUMBER', '质量校验通过阈值[0,1]', '质量通过阈值', '0.6', '{"type":"float","min":0.0,"max":1.0}', 'cleaning', 3, 'rag-cleaning', 'api'),

-- 问答参数
('qa.cache_enabled', 'true', 'BOOLEAN', '问答缓存开关', '问答缓存开关', 'true', '{"type":"bool"}', 'qa', 1, 'rag-backend', 'api'),
('qa.cache_ttl_seconds', '3600', 'NUMBER', '问答缓存有效期(秒)', '缓存有效期', '3600', '{"type":"int","min":60,"max":86400}', 'qa', 2, 'rag-backend', 'kafka'),
('qa.rate_limit_per_second', '10', 'NUMBER', '问答接口每秒请求限制', '问答限流', '10', '{"type":"int","min":1,"max":1000}', 'rate_limit', 1, 'rag-backend', 'api'),

-- 默认限流
('rate_limit.default_per_second', '100', 'NUMBER', '默认接口每秒请求限制', '默认限流', '100', '{"type":"int","min":1,"max":10000}', 'rate_limit', 2, 'rag-backend', 'api'),

-- 审核参数
('review.auto_approve_hours', '24', 'NUMBER', '审核超时自动通过(小时)', '自动审核超时', '24', '{"type":"int","min":0,"max":720}', 'review', 1, 'rag-backend', 'api'),
('review.global_review_enabled', 'true', 'BOOLEAN', '全局审核功能开关', '全局审核开关', 'true', '{"type":"bool"}', 'review', 2, 'rag-backend', 'api'),

-- 文件上传
('upload.max_file_size_mb', '100', 'NUMBER', '单文件上传大小上限(MB)', '文件大小上限', '100', '{"type":"int","min":1,"max":500}', 'upload', 1, 'rag-backend', 'restart');

-- 创建配置变更历史表
CREATE TABLE IF NOT EXISTS system_config_histories (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_id       BIGINT        NOT NULL COMMENT '关联 system_configs.id',
    config_key      VARCHAR(128)  NOT NULL COMMENT '配置键',
    old_value       TEXT          COMMENT '变更前的值',
    new_value       TEXT          COMMENT '变更后的值',
    changed_by      BIGINT        COMMENT '修改人用户ID',
    changed_by_name VARCHAR(64)   COMMENT '修改人用户名',
    changed_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_config_id (config_id),
    INDEX idx_config_key (config_key),
    INDEX idx_changed_at (changed_at)
) COMMENT='配置变更历史(审计追溯)';
