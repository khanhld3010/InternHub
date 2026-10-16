package org.example.internservice.attendance.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.attendance.report.enums.DiligenceStatus;
import org.example.internservice.leave.entity.enums.LeaveType;

import java.time.LocalDate;
import java.util.Map;

/**
 * Dashboard KPI thống kê tổng quan toàn công ty về chuyên cần và nghỉ phép (TM-26).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryKpiResponse {

    private Integer month;
    private Integer year;
    private LocalDate startDate;
    private LocalDate endDate;

    private Integer totalInterns;
    private Double overallDiligenceRate;
    private Integer totalExpectedWorkDays;
    private Double totalActualWorkDays;
    private Long totalOnTimeCount;
    private Long totalLateCount;
    private Long totalEarlyLeaveCount;
    private Double totalApprovedLeaveDays;
    private Double totalUnexcusedAbsenceDays;

    private Map<LeaveType, Double> leaveReasonBreakdown;
    private Map<DiligenceStatus, Long> statusDistribution;
    private Long criticalInternsCount;
}
