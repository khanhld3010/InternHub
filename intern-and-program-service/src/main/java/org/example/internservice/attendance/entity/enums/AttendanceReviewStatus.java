package org.example.internservice.attendance.entity.enums;

/**
 * Trạng thái kiểm duyệt bản ghi chấm công (ADR 0003 & TM-25 v2.0).
 */
public enum AttendanceReviewStatus {
    AUTO_APPROVED,
    PENDING_REVIEW,
    HR_APPROVED,
    HR_REJECTED
}
