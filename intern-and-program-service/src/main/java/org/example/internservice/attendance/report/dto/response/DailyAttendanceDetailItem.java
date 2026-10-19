package org.example.internservice.attendance.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.report.enums.DailyDayStatus;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.example.internservice.leave.entity.enums.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Chi tiết công việc và điểm danh của 1 ngày trong tháng (TM-26).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAttendanceDetailItem {

    private LocalDate date;
    private String dayOfWeek;
    private String dayOfWeekVietnamese;
    private boolean isWeekend;
    private DailyDayStatus dayStatus;
    private String dayStatusDescription;

    private AttendanceInfo attendance;
    private LeaveInfo leaveRequest;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceInfo {
        private Long id;
        private LocalDateTime checkInTime;
        private LocalDateTime checkOutTime;
        private Double totalWorkingHours;
        private Double checkInDistance;
        private String notes;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveInfo {
        private Long id;
        private LeaveType leaveType;
        private String leaveTypeDescription;
        private LeaveDurationType durationType;
        private String reason;
        private String approverName;
        private String attachmentUrl;
    }
}
