package org.example.internservice.mission.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BoardStatus {
    ACTIVE("Đang hoạt động"),
    ARCHIVED("Đã lưu trữ");

    private final String displayName;
}
