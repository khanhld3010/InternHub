-- AI-19: Migration tạo bảng ai_prompt_templates
CREATE TABLE IF NOT EXISTS ai_prompt_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL COMMENT 'Mã logic duy nhất của prompt template',
    template_name VARCHAR(150) NOT NULL COMMENT 'Tên hiển thị trên giao diện quản trị',
    feature_group VARCHAR(50) NOT NULL COMMENT 'SCREENING, INTERVIEW_GEN, INTERVIEW_GRADE, REPORT_GRADE',
    prompt_type VARCHAR(30) NOT NULL DEFAULT 'USER' COMMENT 'SYSTEM hoặc USER',
    version INT NOT NULL DEFAULT 1 COMMENT 'Số thứ tự phiên bản',
    template_content MEDIUMTEXT NOT NULL COMMENT 'Văn bản prompt chứa placeholder {{variable}}',
    description VARCHAR(255) NULL COMMENT 'Mô tả mục đích và bối cảnh sử dụng',
    available_variables_json TEXT NULL COMMENT 'JSON array các biến hỗ trợ',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Cờ kích hoạt phiên bản đang chạy',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_template_code_version UNIQUE (template_code, version),
    INDEX idx_template_code_active (template_code, is_active),
    INDEX idx_feature_group (feature_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
