package org.example.internservice.leave.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Khung thời gian xin nghỉ phép (Cả ngày hoặc nửa ngày) (TM-28).
 */
@Getter
@RequiredArgsConstructor
public enum LeaveDurationType {
    FULL_DAY("Cả ngày"),
    MORNING("Nửa ngày buổi sáng (08:00 - 12:00)"),
    AFTERNOON("Nửa ngày buổi chiều (13:30 - 17:30)");

    private final String description;
}
