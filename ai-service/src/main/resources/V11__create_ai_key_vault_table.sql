-- V11__create_ai_key_vault_table.sql
-- Migration tạo bảng ai_key_vault phục vụ Quản lý Kho Khóa API Đa Nguồn & Failover Round-Robin (AI-10)

CREATE TABLE IF NOT EXISTS ai_key_vault (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_name VARCHAR(100) NOT NULL COMMENT 'Tên gợi nhớ của Key, ví dụ: Gemini Chính, Gemini Dự Phòng 1',
    provider VARCHAR(50) NOT NULL DEFAULT 'GEMINI' COMMENT 'Nhà cung cấp: GEMINI, OPENAI, etc.',
    api_key_encrypted TEXT NOT NULL COMMENT 'Key mã hóa AES-256-GCM',
    api_key_last4 VARCHAR(4) NOT NULL COMMENT '4 ký tự cuối phục vụ nhận diện trên UI',
    api_key_hash VARCHAR(64) NULL COMMENT 'SHA-256 hash của raw key phục vụ chống nhập trùng lặp',
    priority INT NOT NULL DEFAULT 1 COMMENT 'Độ ưu tiên: 1 (Cao nhất), 2, 3...',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, COOLDOWN, DISABLED',
    cooldown_until DATETIME NULL COMMENT 'Mốc thời gian hết hạn cooldown khi dính 429',
    failure_count INT NOT NULL DEFAULT 0 COMMENT 'Số lần lỗi liên tiếp',
    last_used_at DATETIME NULL COMMENT 'Thời điểm gọi gần nhất phục vụ round-robin',
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Xóa mềm',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_vault_dispatcher (provider, is_deleted, status, priority, last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Di chuyển key hiện hành từ ai_global_configs sang ai_key_vault làm Key chính (Priority 1) nếu có
INSERT INTO ai_key_vault (id, key_name, provider, api_key_encrypted, api_key_last4, priority, status, created_by)
SELECT 1, 'Google Gemini Khóa Chính', provider, api_key_encrypted, api_key_last4, 1, IF(is_active = TRUE, 'ACTIVE', 'DISABLED'), updated_by
FROM ai_global_configs WHERE id = 1 AND api_key_encrypted IS NOT NULL
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
