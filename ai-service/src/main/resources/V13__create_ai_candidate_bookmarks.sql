-- V13__create_ai_candidate_bookmarks.sql
-- Quản lý danh sách ứng viên tiềm năng được HR bookmark/ghim theo từng kỳ thực tập

CREATE TABLE IF NOT EXISTS `ai_candidate_bookmarks` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `program_id` BIGINT NOT NULL,
    `intern_id` BIGINT NOT NULL,
    `created_by` VARCHAR(100) DEFAULT NULL,
    `notes` VARCHAR(500) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `uk_ai_bookmark_program_intern` UNIQUE (`program_id`, `intern_id`),
    INDEX `idx_ai_bookmark_program` (`program_id`),
    INDEX `idx_ai_bookmark_intern` (`intern_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
