ALTER TABLE attendances 
  MODIFY COLUMN check_in_distance DOUBLE NULL,
  MODIFY COLUMN check_in_latitude DOUBLE NULL,
  MODIFY COLUMN check_in_longitude DOUBLE NULL,
  MODIFY COLUMN status ENUM('ABSENT','EARLY_LEAVE','LATE','LATE_AND_EARLY_LEAVE','ON_TIME','FORGOTTEN_CHECKOUT') NOT NULL;

SET @dbname = DATABASE();
SET @tablename = "attendances";
SET @columnname = "review_status";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  "SELECT 1",
  "ALTER TABLE attendances ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'AUTO_APPROVED', ADD COLUMN reviewed_by VARCHAR(64) NULL, ADD COLUMN reviewed_at DATETIME NULL, ADD COLUMN review_note TEXT NULL;"
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

CREATE TABLE IF NOT EXISTS attendance_audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  attendance_id BIGINT NULL,
  intern_id BIGINT NOT NULL,
  action_type VARCHAR(20) NOT NULL,
  action_time DATETIME NOT NULL,
  device_fingerprint VARCHAR(128) NOT NULL,
  device_model VARCHAR(100) NULL,
  client_ip VARCHAR(45) NOT NULL,
  is_company_ip BOOLEAN NOT NULL DEFAULT FALSE,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  accuracy_meters DOUBLE NULL,
  distance_meters DOUBLE NULL,
  trust_level VARCHAR(20) NOT NULL,
  audit_flags VARCHAR(255) NULL,
  INDEX idx_audit_intern_id (intern_id),
  INDEX idx_audit_action_time (action_time),
  INDEX idx_audit_device_fingerprint (device_fingerprint),
  INDEX idx_audit_trust_level (trust_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS intern_devices (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  intern_id BIGINT NOT NULL,
  device_fingerprint VARCHAR(128) NOT NULL,
  device_model VARCHAR(100) NULL,
  trust_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAIRING',
  valid_checkin_count INT NOT NULL DEFAULT 0,
  last_used_at DATETIME NOT NULL,
  UNIQUE KEY uk_intern_device (intern_id, device_fingerprint),
  INDEX idx_device_intern_id (intern_id),
  INDEX idx_device_fingerprint (device_fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
