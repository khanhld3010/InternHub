package org.example.internservice.attendance.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.report.enums.DiligenceStatus;

import java.util.List;

/**
 * Báo cáo chi tiết bảng công và các ngày nghỉ phép của 1 Thực tập sinh cụ thể (TM-26).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternAttendanceDetailReportResponse {

    private Long internId;
    private String internCode;
    private String fullName;
    private String email;
    private String departmentName;
    private String programTitle;
    private String mentorName;
    private Integer month;
    private Integer year;
    private Boolean isAllowanceLocked;
    private String allowanceStatus;

    private InternDiligenceSummary summary;
    private List<DailyAttendanceDetailItem> dailyDetails;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternDiligenceSummary {
        private Integer expectedWorkDays;
        private Double actualWorkDays;
        private Long onTimeCount;
        private Long lateCount;
        private Long earlyLeaveCount;
        private Double totalWorkingHours;
        private Double approvedLeaveDays;
        private Double unexcusedAbsenceDays;
        private Double diligenceRate;
        private DiligenceStatus diligenceStatus;
    }
}
