package org.example.internservice.intern.entity.enums;

/**
 * 7 trạng thái vòng đời chuẩn hóa của Thực tập sinh (InternHub Single Source of Truth).
 */
public enum InternStatus {
    PENDING,
    APPROVED,
    INTERNING,
    ON_HOLD,
    COMPLETED,
    REJECTED,
    TERMINATED;

    public boolean canTransitionTo(InternStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == INTERNING || target == ON_HOLD || target == REJECTED;
            case INTERNING -> target == COMPLETED || target == ON_HOLD || target == TERMINATED;
            case ON_HOLD -> target == INTERNING || target == TERMINATED;
            case COMPLETED, TERMINATED -> false; // Trạng thái kết thúc (Terminal states)
            case REJECTED -> target == PENDING; // Cho phép tái kích hoạt / nộp lại nếu được chấp thuận
        };
    }
}
