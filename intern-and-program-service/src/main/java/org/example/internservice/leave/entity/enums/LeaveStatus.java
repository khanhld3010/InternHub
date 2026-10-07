package org.example.internservice.leave.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái xét duyệt đơn xin nghỉ phép của thực tập sinh (TM-28).
 */
@Getter
@RequiredArgsConstructor
public enum LeaveStatus {
    PENDING("Chờ xét duyệt"),
    APPROVED("Đã phê duyệt"),
    REJECTED("Bị từ chối"),
    CANCELLED("Đã hủy bỏ");

    private final String description;

    public boolean canTransitionTo(LeaveStatus targetStatus) {
        if (this == PENDING) {
            return targetStatus == APPROVED || targetStatus == REJECTED || targetStatus == CANCELLED;
        }
        return false;
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }
}
