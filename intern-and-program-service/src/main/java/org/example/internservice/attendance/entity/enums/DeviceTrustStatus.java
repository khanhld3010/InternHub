package org.example.internservice.attendance.entity.enums;

/**
 * Trạng thái tin cậy của thiết bị di động trong vòng đời điểm danh (ADR 0003 & TM-25 v2.0).
 */
public enum DeviceTrustStatus {
    PRIMARY_TRUSTED,
    PENDING_PAIRING,
    BLOCKED
}
