package org.example.internservice.intern.entity.enums;

public enum InternStatus {
    PENDING,
    APPROVED,
    INTERNING,
    COMPLETED,
    REJECTED;

    public boolean canTransitionTo(InternStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == INTERNING || target == REJECTED;
            case INTERNING -> target == COMPLETED || target == REJECTED;
            case COMPLETED -> false;
            case REJECTED -> target == PENDING;
        };
    }
}
