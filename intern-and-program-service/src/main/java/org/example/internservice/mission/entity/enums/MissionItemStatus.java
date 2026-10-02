package org.example.internservice.mission.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 3 trạng thái công việc chuẩn hóa của MissionItem (Mô hình Kanban 3 cột)
 */
@Getter
@RequiredArgsConstructor
public enum MissionItemStatus {
    TODO("Chưa làm"),
    IN_PROGRESS("Đang làm"),
    COMPLETED("Hoàn thiện");

    private final String displayName;
}
