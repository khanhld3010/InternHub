-- Migration thêm Permissions cho Module C6: AI Service
USE internhub_db;

-- 1. Thêm permission AI_CHAT_USE (sử dụng Chatbot AI)
INSERT INTO permissions (code, name, module, description, created_at, updated_at)
SELECT 'AI_CHAT_USE', 'Sử dụng Trợ lý ảo AI Chatbot', 'AI', 'Quyền sử dụng Trợ lý ảo AI Chatbot dành cho Thực tập sinh', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AI_CHAT_USE');

-- 2. Thêm permission AI_CONFIG_MANAGE (quản lý cấu hình AI Service)
INSERT INTO permissions (code, name, module, description, created_at, updated_at)
SELECT 'AI_CONFIG_MANAGE', 'Quản lý cấu hình AI Service', 'AI', 'Quyền quản trị và thiết lập cấu hình AI Service', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code = 'AI_CONFIG_MANAGE');

-- 3. Gán quyền AI_CHAT_USE cho Role INTERN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_INTERN' AND p.code = 'AI_CHAT_USE'
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- 4. Gán quyền AI_CONFIG_MANAGE cho Role ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.code = 'AI_CONFIG_MANAGE'
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
