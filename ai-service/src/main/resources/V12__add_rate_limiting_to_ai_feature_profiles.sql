-- V12__add_rate_limiting_to_ai_feature_profiles.sql
-- Thêm cấu hình Rate Limiting & Quota động theo từng Feature Profile

ALTER TABLE ai_feature_profiles
ADD COLUMN rate_limit_per_minute INT NOT NULL DEFAULT 20 COMMENT 'Giới hạn request / phút',
ADD COLUMN daily_quota INT NOT NULL DEFAULT 200 COMMENT 'Hạn ngạch request / ngày';

-- Cập nhật hạn mức chuẩn cho từng module tính năng theo thỏa thuận thiết kế AI-12
UPDATE ai_feature_profiles
SET rate_limit_per_minute = 20, daily_quota = 200
WHERE feature_key = 'CHATBOT';

UPDATE ai_feature_profiles
SET rate_limit_per_minute = 5, daily_quota = 50
WHERE feature_key = 'INTERVIEW_GENERATOR';

UPDATE ai_feature_profiles
SET rate_limit_per_minute = 20, daily_quota = 100
WHERE feature_key = 'REPORT_ASSISTANT';

UPDATE ai_feature_profiles
SET rate_limit_per_minute = 3, daily_quota = 10
WHERE feature_key = 'CV_SCREENING';
