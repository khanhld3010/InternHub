package org.example.internservice.attendance.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái công việc và sự hiện diện của thực tập sinh trong từng ngày (TM-26).
 */
@Getter
@RequiredArgsConstructor
public enum DailyDayStatus {

    PRESENT_ON_TIME("Đi làm đúng giờ"),
    PRESENT_LATE("Đi làm muộn"),
    PRESENT_EARLY_LEAVE("Về sớm"),
    PRESENT_LATE_AND_EARLY_LEAVE("Vừa đi muộn vừa về sớm"),
    LEAVE_APPROVED("Nghỉ phép có duyệt"),
    LEAVE_PENDING("Đơn xin nghỉ chờ duyệt"),
    ABSENT_UNEXCUSED("Vắng mặt không phép"),
    WEEKEND("Ngày nghỉ cuối tuần"),
    NOT_ENROLLED("Chưa onboard / Đã kết thúc"),
    FUTURE_DAY("Ngày chưa diễn ra");

    private final String description;
}
