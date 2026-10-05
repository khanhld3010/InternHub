package org.example.internservice.mission.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MissionPriority {
    LOW("Thấp"),
    MEDIUM("Trung bình"),
    HIGH("Cao");

    private final String displayName;
}
