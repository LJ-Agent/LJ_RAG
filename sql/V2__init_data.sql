-- =============================================
-- RAG知识库系统 - 初始数据
-- =============================================

USE rag_db;

-- =============================================
-- 初始化角色
-- =============================================
INSERT INTO roles (role_name, role_code, description) VALUES
('超级管理员',  'SUPER_ADMIN', '拥有所有权限'),
('管理员',      'ADMIN',       '系统管理员，管理用户和配置'),
('审核员',      'REVIEWER',    '负责文档审核'),
('编辑者',      'EDITOR',      '可以上传和管理文档'),
('查看者',      'VIEWER',      '只能查看和问答');

-- =============================================
-- 初始化权限
-- =============================================
INSERT INTO permissions (permission_name, permission_code, resource_type, description) VALUES
('创建用户',   'USER:CREATE',   'USER', '创建新用户'),
('编辑用户',   'USER:UPDATE',   'USER', '编辑用户信息'),
('删除用户',   'USER:DELETE',   'USER', '删除用户'),
('查看用户',   'USER:VIEW',     'USER', '查看用户列表'),
('上传文档',   'DOCUMENT:UPLOAD',   'DOCUMENT', '上传文档'),
('删除文档',   'DOCUMENT:DELETE',   'DOCUMENT', '删除文档'),
('查看文档',   'DOCUMENT:VIEW',     'DOCUMENT', '查看文档'),
('审核文档',   'REVIEW:APPROVE',    'REVIEW', '审核通过/驳回文档'),
('查看审核',   'REVIEW:VIEW',       'REVIEW', '查看审核记录'),
('创建知识库', 'KB:CREATE',    'KB', '创建知识库'),
('编辑知识库', 'KB:UPDATE',    'KB', '编辑知识库'),
('删除知识库', 'KB:DELETE',    'KB', '删除知识库'),
('查看知识库', 'KB:VIEW',      'KB', '查看知识库'),
('管理系统配置', 'CONFIG:MANAGE', 'SYSTEM', '管理系统配置'),
('问答提问',   'QA:ASK',       'QA', '使用问答功能'),
('查看问答记录', 'QA:HISTORY',  'QA', '查看问答历史'),
('查看反馈',   'FEEDBACK:VIEW',   'FEEDBACK', '查看用户反馈'),
('处理反馈',   'FEEDBACK:HANDLE', 'FEEDBACK', '处理用户反馈');

-- =============================================
-- 初始化角色-权限关联
-- =============================================
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE role_code = 'SUPER_ADMIN'), id FROM permissions;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN (
    'USER:CREATE', 'USER:UPDATE', 'USER:VIEW',
    'DOCUMENT:UPLOAD', 'DOCUMENT:DELETE', 'DOCUMENT:VIEW',
    'REVIEW:APPROVE', 'REVIEW:VIEW',
    'KB:CREATE', 'KB:UPDATE', 'KB:DELETE', 'KB:VIEW',
    'CONFIG:MANAGE', 'FEEDBACK:VIEW', 'FEEDBACK:HANDLE'
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_code = 'REVIEWER'
  AND p.permission_code IN ('DOCUMENT:VIEW', 'REVIEW:APPROVE', 'REVIEW:VIEW', 'KB:VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_code = 'EDITOR'
  AND p.permission_code IN (
    'DOCUMENT:UPLOAD', 'DOCUMENT:VIEW',
    'KB:CREATE', 'KB:UPDATE', 'KB:VIEW',
    'QA:ASK', 'QA:HISTORY'
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_code = 'VIEWER'
  AND p.permission_code IN ('DOCUMENT:VIEW', 'KB:VIEW', 'QA:ASK', 'QA:HISTORY');

-- =============================================
-- 初始化系统配置
-- =============================================
INSERT INTO system_configs (config_key, config_value, config_type, description) VALUES
('review.auto_approve_hours',      '24',    'NUMBER', '审核超时自动通过小时数'),
('review.global_review_enabled',   'true',  'BOOLEAN', '全局审核开关'),
('qa.cache_enabled',               'true',  'BOOLEAN', '问答缓存开关'),
('qa.cache_ttl_seconds',           '3600',  'NUMBER', '问答缓存有效期(秒)'),
('qa.rate_limit_per_second',       '10',    'NUMBER', '问答每秒请求限制'),
('file.max_upload_size_mb',        '100',   'NUMBER', '文件上传大小限制(MB)'),
('file.allowed_types',             'pdf,docx,txt,md,csv,xlsx', 'STRING', '允许上传的文件类型');

-- =============================================
-- 初始化超级管理员用户 (密码: admin123)
-- =============================================
INSERT INTO users (username, password_hash, real_name, email, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8.iAt2HHOawi6v4PVcqCgUB6BHBG6', '系统管理员', 'admin@example.com', 1);

INSERT INTO user_roles (user_id, role_id)
SELECT (SELECT id FROM users WHERE username = 'admin'),
       (SELECT id FROM roles WHERE role_code = 'SUPER_ADMIN');
