package org.example.internservice.program.entity.enums;

import lombok.Getter;

@Getter
public enum ProgramStatus {
    PLANNING("Kế hoạch"),
    OPEN("Đang nhận hồ sơ"),
    ONGOING("Đang diễn ra"),
    COMPLETED("Đã kết thúc"),
    CANCELLED("Đã hủy");

    private final String displayName;

    ProgramStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean canTransitionTo(ProgramStatus target) {
        if (target == null || this == target) {
            return false;
        }

        return switch (this) {
            case PLANNING -> target == OPEN || target == ONGOING || target == CANCELLED;
            case OPEN -> target == ONGOING || target == CANCELLED;
            case ONGOING -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false; // Terminal states
        };
    }
}
