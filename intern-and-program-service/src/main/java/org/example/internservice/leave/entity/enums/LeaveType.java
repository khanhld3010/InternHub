package org.example.internservice.leave.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Phân loại hình thức nghỉ phép của thực tập sinh (TM-28).
 */
@Getter
@RequiredArgsConstructor
public enum LeaveType {
    SICK("Nghỉ ốm đau / Khám bệnh"),
    PERSONAL("Nghỉ việc riêng cá nhân"),
    ACADEMIC_EXAM("Nghỉ thi cử / Đồ án tốt nghiệp"),
    BEREAVEMENT("Nghỉ việc gia đình / Tang lễ"),
    OTHER("Lý do chính đáng khác");

    private final String description;
}
