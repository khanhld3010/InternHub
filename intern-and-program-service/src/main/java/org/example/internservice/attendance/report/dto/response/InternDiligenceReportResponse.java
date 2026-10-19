package org.example.internservice.attendance.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.report.enums.DiligenceStatus;

/**
 * Báo cáo tổng hợp kết quả chuyên cần của từng Thực tập sinh trong danh sách phân trang (TM-26).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternDiligenceReportResponse {

    private Long internId;
    private String internCode;
    private String fullName;
    private String email;
    private String departmentName;
    private String programTitle;
    private String mentorName;

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
    private String diligenceStatusTitle;
    private String warningNotes;
}
