-- V10__create_ai_multi_profile_tables.sql
-- Migration tách cấu trúc ai_global_configs (API Key Vault) và ai_feature_profiles (Module Profiles)

CREATE TABLE IF NOT EXISTS ai_global_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(50) NOT NULL DEFAULT 'GEMINI',
    api_key_encrypted TEXT NULL,
    api_key_last4 VARCHAR(4) NULL,
    key_version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_feature_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feature_key VARCHAR(50) NOT NULL UNIQUE,
    feature_name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    model_name VARCHAR(100) NOT NULL DEFAULT 'gemini-3.5-flash-lite',
    temperature DECIMAL(3, 2) NOT NULL DEFAULT 0.20,
    max_tokens INT NOT NULL DEFAULT 1024,
    system_instruction TEXT NULL,
    quick_prompts_json TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Di chuyển dữ liệu cũ từ ai_system_configs sang ai_global_configs (nếu có)
INSERT INTO ai_global_configs (id, provider, api_key_encrypted, api_key_last4, key_version, is_active, updated_by)
SELECT 1, provider, api_key_encrypted, api_key_last4, key_version, is_active, updated_by
FROM ai_system_configs WHERE id = 1
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- Khởi tạo 4 Profiles chuẩn mặc định
INSERT INTO ai_feature_profiles (feature_key, feature_name, description, model_name, temperature, max_tokens, system_instruction, quick_prompts_json, is_active)
VALUES
('CHATBOT', 'Trợ Lý FAQ Thực Tập', 'Hỗ trợ giải đáp nội quy, tài liệu onboarding cho thực tập sinh.', 'gemini-3.5-flash-lite', 0.30, 1024, 'Bạn là trợ lý ảo hỗ trợ thực tập sinh tại InternHub. Trả lời thân thiện, súc tích và chính xác dựa trên quy định công ty.', '["Quy định điểm danh và giải trình thế nào?","Quy trình xin nghỉ phép (TM-28) cần báo trước mấy ngày?","Thời gian chốt công và nhận phụ cấp hàng tháng?","Cách ký hợp đồng điện tử v2.0 như thế nào?"]', TRUE),
('CV_SCREENING', 'Sàng Lọc Hồ Sơ & CV', 'Trích xuất kỹ năng, so khớp thang đo rubric tuyển dụng, xuất JSON 100% chuẩn xác.', 'gemini-3.5-flash-lite', 0.00, 2048, 'Bạn là chuyên gia thẩm định hồ sơ tuyển dụng. Phân tích bám sát tiêu chí rubric, không thêm thắt suy diễn.', '[]', TRUE),
('INTERVIEW_GENERATOR', 'Sinh Bộ Câu Hỏi Phỏng Vấn', 'Tạo bộ câu hỏi phỏng vấn kỹ thuật và tình huống đa dạng, sáng tạo, chống lặp lại.', 'gemini-3.5-flash-lite', 0.65, 3072, 'Bạn là chuyên gia phỏng vấn kỹ thuật công nghệ cao. Tạo câu hỏi phân loại sâu sắc, tình huống thực tế.', '[]', TRUE),
('REPORT_ASSISTANT', 'Trợ Lý Đánh Giá Báo Cáo Tuần', 'Đối soát báo cáo với tiến độ Kanban, phân tích 3 dòng và gợi ý điểm 5 sao khách quan.', 'gemini-3.5-flash-lite', 0.10, 2048, 'BẠN LÀ MỘT TRỢ LÝ GIẢNG VIÊN VÀ CHUYÊN GIA ĐỐI SOÁT THỰC TẬP CÔNG NGHỆ THÔNG TIN CẤP CAO. Nhiệm vụ của bạn là đọc báo cáo tuần của Thực tập sinh, đối chiếu với danh sách các nhiệm vụ trên bảng Kanban của tuần đó, và cung cấp bản phân tích cô đọng 3 dòng cùng đề xuất điểm số (THANG ĐIỂM TỪ 1 ĐẾN 5 SAO).', '[]', TRUE)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
