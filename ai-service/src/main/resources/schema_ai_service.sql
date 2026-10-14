-- Schema khởi tạo cho ai_service_db
CREATE DATABASE IF NOT EXISTS ai_service_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_service_db;

CREATE TABLE IF NOT EXISTS ai_system_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(50) NOT NULL DEFAULT 'GEMINI',
    model_name VARCHAR(100) NOT NULL DEFAULT 'gemini-1.5-flash',
    api_key_encrypted TEXT NULL,
    api_key_last4 CHAR(4) NULL,
    key_version INT NOT NULL DEFAULT 1,
    updated_by VARCHAR(64) NULL,
    temperature DECIMAL(3,2) NOT NULL DEFAULT 0.30,
    max_tokens INT NOT NULL DEFAULT 1024,
    system_instruction TEXT NULL,
    quick_prompts_json TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_chat_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id CHAR(36) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NULL,
    provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    latency_ms INT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_chat_logs_user_created (user_id, created_at),
    INDEX idx_ai_chat_logs_created (created_at)
);

CREATE TABLE IF NOT EXISTS ai_config_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    changed_by VARCHAR(64) NOT NULL,
    changed_fields VARCHAR(500) NOT NULL,
    old_values_json TEXT NULL,
    new_values_json TEXT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_config_audit_created (created_at)
);

-- Dữ liệu mẫu khởi tạo cho cấu hình AI
INSERT INTO ai_system_configs (
    provider, model_name, temperature, max_tokens, 
    system_instruction, quick_prompts_json, is_active
) VALUES (
    'GEMINI',
    'gemini-1.5-flash',
    0.30,
    1024,
    'Bạn là Trợ lý ảo AI của nền tảng InternHub, hỗ trợ giải đáp thắc mắc cho thực tập sinh dựa trên quy chế công ty (điểm danh, xin nghỉ phép, phụ cấp, hợp đồng). Hãy trả lời ngắn gọn, thân thiện, chính xác. Tuyệt đối không bàn luận các chủ đề ngoài công việc và không chia sẻ bí mật kỹ thuật.',
    '["Quy định điểm danh và giải trình thế nào?","Quy trình xin nghỉ phép (TM-28) cần báo trước mấy ngày?","Thời gian chốt công và nhận phụ cấp hàng tháng?","Cách ký hợp đồng điện tử v2.0 như thế nào?"]',
    TRUE
);
