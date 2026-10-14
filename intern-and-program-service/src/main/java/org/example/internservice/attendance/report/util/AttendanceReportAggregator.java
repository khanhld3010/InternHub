package org.example.internservice.attendance.report.util;

import lombok.RequiredArgsConstructor;
import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.report.dto.request.AttendanceReportFilterRequest;
import org.example.internservice.attendance.report.dto.response.AttendanceSummaryKpiResponse;
import org.example.internservice.attendance.report.dto.response.DailyAttendanceDetailItem;
import org.example.internservice.attendance.report.dto.response.InternDiligenceReportResponse;
import org.example.internservice.attendance.report.enums.DailyDayStatus;
import org.example.internservice.attendance.report.enums.DiligenceStatus;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.leave.entity.LeaveRequest;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.example.internservice.leave.entity.enums.LeaveType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Tiện ích tính toán chuyên cần, điểm danh chi tiết và tổng hợp KPI dashboard (TM-26).
 * Đảm bảo tuân thủ tiêu chuẩn Anti-God-Class (Rule 17).
 */
@Component
@RequiredArgsConstructor
public class AttendanceReportAggregator {

    private final WorkDayCalculator workDayCalculator;

    public InternDiligenceReportResponse calculateInternDiligence(
            InternProfile intern,
            LocalDate startDate,
            LocalDate endDate,
            List<Attendance> attendances,
            List<LeaveRequest> leaves,
            String departmentName,
            String programTitle) {

        int expectedWorkDays = workDayCalculator.calculateExpectedWorkDays(intern, startDate, endDate);

        double actualWorkDays = 0.0;
        long onTimeCount = 0;
        long lateCount = 0;
        long earlyLeaveCount = 0;
        double totalHours = 0.0;

        for (Attendance a : attendances) {
            boolean hasHalfDayLeave = leaves.stream().anyMatch(l ->
                    l.getDurationType() != LeaveDurationType.FULL_DAY
                            && !a.getWorkDate().isBefore(l.getStartDate())
                            && !a.getWorkDate().isAfter(l.getEndDate()));
            actualWorkDays += hasHalfDayLeave ? 0.5 : 1.0;
            if (a.getStatus() == AttendanceStatus.ON_TIME) onTimeCount++;
            if (a.getStatus() == AttendanceStatus.LATE || a.getStatus() == AttendanceStatus.LATE_AND_EARLY_LEAVE) lateCount++;
            if (a.getStatus() == AttendanceStatus.EARLY_LEAVE || a.getStatus() == AttendanceStatus.LATE_AND_EARLY_LEAVE) earlyLeaveCount++;
            if (a.getTotalWorkingHours() != null) totalHours += a.getTotalWorkingHours();
        }
        totalHours = Math.round(totalHours * 100.0) / 100.0;

        double approvedLeaveDays = leaves.stream()
                .mapToDouble(l -> l.getTotalDays() != null ? l.getTotalDays() : (l.getDurationType() == LeaveDurationType.FULL_DAY ? 1.0 : 0.5))
                .sum();

        double unexcusedAbsence = Math.max(0.0, expectedWorkDays - (actualWorkDays + approvedLeaveDays));
        double diligenceRate = expectedWorkDays <= 0 ? 100.0 : Math.min(100.0, Math.max(0.0, Math.round(((actualWorkDays + approvedLeaveDays) / expectedWorkDays) * 10000.0) / 100.0));

        DiligenceStatus status = evaluateDiligenceStatus(diligenceRate, lateCount, unexcusedAbsence);
        String warningNotes = buildWarningNotes(lateCount, unexcusedAbsence);

        return InternDiligenceReportResponse.builder()
                .internId(intern.getId())
                .internCode(intern.getInternCode())
                .fullName(intern.getFullName())
                .email(intern.getEmail())
                .departmentName(departmentName)
                .programTitle(programTitle)
                .mentorName(intern.getMentorName())
                .expectedWorkDays(expectedWorkDays)
                .actualWorkDays(actualWorkDays)
                .onTimeCount(onTimeCount)
                .lateCount(lateCount)
                .earlyLeaveCount(earlyLeaveCount)
                .totalWorkingHours(totalHours)
                .approvedLeaveDays(approvedLeaveDays)
                .unexcusedAbsenceDays(unexcusedAbsence)
                .diligenceRate(diligenceRate)
                .diligenceStatus(status)
                .diligenceStatusTitle(status.getTitle())
                .warningNotes(warningNotes)
                .build();
    }

    public DiligenceStatus evaluateDiligenceStatus(double rate, long lateCount, double unexcused) {
        if (rate < 70.0 || lateCount > 5 || unexcused >= 2.0) return DiligenceStatus.CRITICAL;
        if (rate < 85.0 || lateCount >= 3 || unexcused == 1.0) return DiligenceStatus.WARNING;
        if (rate >= 95.0 && lateCount == 0 && unexcused == 0.0) return DiligenceStatus.EXCELLENT;
        return DiligenceStatus.GOOD;
    }

    public String buildWarningNotes(long lateCount, double unexcused) {
        List<String> notes = new ArrayList<>();
        if (lateCount > 0) notes.add("Đi muộn " + lateCount + " lần trong kỳ");
        if (unexcused > 0) notes.add("Vắng mặt không phép " + (unexcused == (long) unexcused ? String.format("%d", (long) unexcused) : String.format("%.1f", unexcused)) + " ngày");
        return notes.isEmpty() ? "Chuyên cần tốt, không có vi phạm" : String.join("; ", notes);
    }

    public List<DailyAttendanceDetailItem> buildDailyDetails(
            InternProfile intern,
            LocalDate start,
            LocalDate end,
            Map<LocalDate, Attendance> attendanceMap,
            List<LeaveRequest> leaves) {

        List<DailyAttendanceDetailItem> list = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            final LocalDate currentDay = d;
            boolean isWeekend = currentDay.getDayOfWeek().getValue() >= 6;
            DailyDayStatus dayStatus;
            Attendance att = attendanceMap.get(currentDay);
            LeaveRequest approvedLeave = leaves.stream()
                    .filter(l -> !currentDay.isBefore(l.getStartDate()) && !currentDay.isAfter(l.getEndDate()))
                    .findFirst()
                    .orElse(null);

            if (currentDay.isAfter(today)) dayStatus = DailyDayStatus.FUTURE_DAY;
            else if (isWeekend) dayStatus = DailyDayStatus.WEEKEND;
            else if ((intern.getStartDate() != null && currentDay.isBefore(intern.getStartDate())) || (intern.getEndDate() != null && currentDay.isAfter(intern.getEndDate()))) dayStatus = DailyDayStatus.NOT_ENROLLED;
            else if (att != null) dayStatus = mapAttendanceToDayStatus(att.getStatus());
            else if (approvedLeave != null) dayStatus = DailyDayStatus.LEAVE_APPROVED;
            else dayStatus = DailyDayStatus.ABSENT_UNEXCUSED;

            list.add(DailyAttendanceDetailItem.builder()
                    .date(currentDay)
                    .dayOfWeek(currentDay.getDayOfWeek().name())
                    .dayOfWeekVietnamese(workDayCalculator.getDayOfWeekVietnamese(currentDay.getDayOfWeek()))
                    .isWeekend(isWeekend)
                    .dayStatus(dayStatus)
                    .dayStatusDescription(dayStatus.getDescription())
                    .attendance(att == null ? null : DailyAttendanceDetailItem.AttendanceInfo.builder()
                            .id(att.getId())
                            .checkInTime(att.getCheckInTime())
                            .checkOutTime(att.getCheckOutTime())
                            .totalWorkingHours(att.getTotalWorkingHours())
                            .checkInDistance(att.getCheckInDistance())
                            .notes(att.getNotes())
                            .build())
                    .leaveRequest(approvedLeave == null ? null : DailyAttendanceDetailItem.LeaveInfo.builder()
                            .id(approvedLeave.getId())
                            .leaveType(approvedLeave.getLeaveType())
                            .leaveTypeDescription(approvedLeave.getLeaveType().getDescription())
                            .durationType(approvedLeave.getDurationType())
                            .reason(approvedLeave.getReason())
                            .approverName(approvedLeave.getApproverName())
                            .attachmentUrl(approvedLeave.getAttachmentUrl())
                            .build())
                    .build());
        }
        return list;
    }

    public DailyDayStatus mapAttendanceToDayStatus(AttendanceStatus status) {
        if (status == null) return DailyDayStatus.PRESENT_ON_TIME;
        return switch (status) {
            case ON_TIME -> DailyDayStatus.PRESENT_ON_TIME;
            case LATE -> DailyDayStatus.PRESENT_LATE;
            case EARLY_LEAVE -> DailyDayStatus.PRESENT_EARLY_LEAVE;
            case LATE_AND_EARLY_LEAVE -> DailyDayStatus.PRESENT_LATE_AND_EARLY_LEAVE;
            case FORGOTTEN_CHECKOUT, ABSENT -> DailyDayStatus.ABSENT_UNEXCUSED;
            default -> DailyDayStatus.ABSENT_UNEXCUSED;
        };
    }

    public AttendanceSummaryKpiResponse aggregateKpi(
            AttendanceReportFilterRequest filter,
            LocalDate start,
            LocalDate end,
            List<InternDiligenceReportResponse> reports,
            List<LeaveRequest> leaves) {

        Map<LeaveType, Double> reasonMap = new EnumMap<>(LeaveType.class);
        for (LeaveType t : LeaveType.values()) reasonMap.put(t, 0.0);
        leaves.forEach(l -> reasonMap.merge(l.getLeaveType(), l.getTotalDays() != null ? l.getTotalDays() : 1.0, Double::sum));

        Map<DiligenceStatus, Long> distMap = new EnumMap<>(DiligenceStatus.class);
        for (DiligenceStatus s : DiligenceStatus.values()) distMap.put(s, 0L);
        reports.forEach(r -> distMap.merge(r.getDiligenceStatus(), 1L, Long::sum));

        double avgRate = reports.stream().mapToDouble(InternDiligenceReportResponse::getDiligenceRate).average().orElse(0.0);
        avgRate = Math.round(avgRate * 100.0) / 100.0;

        return AttendanceSummaryKpiResponse.builder()
                .month(filter.getMonth() != null ? filter.getMonth() : start.getMonthValue())
                .year(filter.getYear() != null ? filter.getYear() : start.getYear())
                .startDate(start)
                .endDate(end)
                .totalInterns(reports.size())
                .overallDiligenceRate(avgRate)
                .totalExpectedWorkDays(reports.stream().mapToInt(r -> r.getExpectedWorkDays() != null ? r.getExpectedWorkDays() : 0).sum())
                .totalActualWorkDays(reports.stream().mapToDouble(r -> r.getActualWorkDays() != null ? r.getActualWorkDays() : 0.0).sum())
                .totalOnTimeCount(reports.stream().mapToLong(r -> r.getOnTimeCount() != null ? r.getOnTimeCount() : 0).sum())
                .totalLateCount(reports.stream().mapToLong(r -> r.getLateCount() != null ? r.getLateCount() : 0).sum())
                .totalEarlyLeaveCount(reports.stream().mapToLong(r -> r.getEarlyLeaveCount() != null ? r.getEarlyLeaveCount() : 0).sum())
                .totalApprovedLeaveDays(reports.stream().mapToDouble(r -> r.getApprovedLeaveDays() != null ? r.getApprovedLeaveDays() : 0.0).sum())
                .totalUnexcusedAbsenceDays(reports.stream().mapToDouble(r -> r.getUnexcusedAbsenceDays() != null ? r.getUnexcusedAbsenceDays() : 0.0).sum())
                .leaveReasonBreakdown(reasonMap)
                .statusDistribution(distMap)
                .criticalInternsCount(distMap.get(DiligenceStatus.CRITICAL))
                .build();
    }

    public AttendanceSummaryKpiResponse buildEmptyKpiResponse(
            AttendanceReportFilterRequest filter,
            LocalDate start,
            LocalDate end) {

        Map<LeaveType, Double> reasonMap = new EnumMap<>(LeaveType.class);
        for (LeaveType t : LeaveType.values()) reasonMap.put(t, 0.0);

        Map<DiligenceStatus, Long> distMap = new EnumMap<>(DiligenceStatus.class);
        for (DiligenceStatus s : DiligenceStatus.values()) distMap.put(s, 0L);

        return AttendanceSummaryKpiResponse.builder()
                .month(filter.getMonth() != null ? filter.getMonth() : start.getMonthValue())
                .year(filter.getYear() != null ? filter.getYear() : start.getYear())
                .startDate(start)
                .endDate(end)
                .totalInterns(0)
                .overallDiligenceRate(100.0)
                .totalExpectedWorkDays(0)
                .totalActualWorkDays(0.0)
                .totalOnTimeCount(0L)
                .totalLateCount(0L)
                .totalEarlyLeaveCount(0L)
                .totalApprovedLeaveDays(0.0)
                .totalUnexcusedAbsenceDays(0.0)
                .leaveReasonBreakdown(reasonMap)
                .statusDistribution(distMap)
                .criticalInternsCount(0L)
                .build();
    }
}
