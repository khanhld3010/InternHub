package org.example.internservice.attendance.report.util;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests - AttendanceReportAggregator")
class AttendanceReportAggregatorTest {

    @Mock
    private WorkDayCalculator workDayCalculator;

    @InjectMocks
    private AttendanceReportAggregator aggregator;

    private InternProfile intern;

    @BeforeEach
    void setUp() {
        intern = InternProfile.builder()
                .internCode("INT-001")
                .fullName("Thực Tập Sinh Test")
                .email("test@internhub.com")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();
        intern.setId(10L);
    }

    @Test
    @DisplayName("Đánh giá đúng các ngưỡng xếp loại chuyên cần (DiligenceStatus)")
    void evaluateDiligenceStatus_thresholds() {
        // EXCELLENT: rate >= 95, late == 0, unexcused == 0
        assertEquals(DiligenceStatus.EXCELLENT, aggregator.evaluateDiligenceStatus(98.0, 0, 0.0));

        // GOOD: rate >= 85, late < 3, unexcused < 1
        assertEquals(DiligenceStatus.GOOD, aggregator.evaluateDiligenceStatus(90.0, 1, 0.0));

        // WARNING: rate < 85 hoặc late >= 3 hoặc unexcused == 1
        assertEquals(DiligenceStatus.WARNING, aggregator.evaluateDiligenceStatus(82.0, 1, 0.0));
        assertEquals(DiligenceStatus.WARNING, aggregator.evaluateDiligenceStatus(92.0, 3, 0.0));
        assertEquals(DiligenceStatus.WARNING, aggregator.evaluateDiligenceStatus(90.0, 0, 1.0));

        // CRITICAL: rate < 70 hoặc late > 5 hoặc unexcused >= 2
        assertEquals(DiligenceStatus.CRITICAL, aggregator.evaluateDiligenceStatus(65.0, 0, 0.0));
        assertEquals(DiligenceStatus.CRITICAL, aggregator.evaluateDiligenceStatus(90.0, 6, 0.0));
        assertEquals(DiligenceStatus.CRITICAL, aggregator.evaluateDiligenceStatus(90.0, 0, 2.0));
    }

    @Test
    @DisplayName("Xây dựng chuỗi cảnh báo vi phạm đi muộn và vắng vô cớ")
    void buildWarningNotes_variousViolations() {
        assertEquals("Chuyên cần tốt, không có vi phạm", aggregator.buildWarningNotes(0, 0.0));
        assertEquals("Đi muộn 2 lần trong kỳ", aggregator.buildWarningNotes(2, 0.0));
        assertEquals("Vắng mặt không phép 1 ngày", aggregator.buildWarningNotes(0, 1.0));
        assertEquals("Đi muộn 3 lần trong kỳ; Vắng mặt không phép 1.5 ngày", aggregator.buildWarningNotes(3, 1.5));
    }

    @Test
    @DisplayName("Map trạng thái AttendanceStatus sang DailyDayStatus")
    void mapAttendanceToDayStatus_mapping() {
        assertEquals(DailyDayStatus.PRESENT_ON_TIME, aggregator.mapAttendanceToDayStatus(AttendanceStatus.ON_TIME));
        assertEquals(DailyDayStatus.PRESENT_LATE, aggregator.mapAttendanceToDayStatus(AttendanceStatus.LATE));
        assertEquals(DailyDayStatus.PRESENT_EARLY_LEAVE, aggregator.mapAttendanceToDayStatus(AttendanceStatus.EARLY_LEAVE));
        assertEquals(DailyDayStatus.PRESENT_LATE_AND_EARLY_LEAVE, aggregator.mapAttendanceToDayStatus(AttendanceStatus.LATE_AND_EARLY_LEAVE));
        assertEquals(DailyDayStatus.ABSENT_UNEXCUSED, aggregator.mapAttendanceToDayStatus(AttendanceStatus.ABSENT));
        assertEquals(DailyDayStatus.PRESENT_ON_TIME, aggregator.mapAttendanceToDayStatus(null));
    }

    @Test
    @DisplayName("Tính toán chi tiết bảng công của TTS kèm đơn nghỉ phép nửa ngày")
    void calculateInternDiligence_withHalfDayLeave_calculatesAccurately() {
        when(workDayCalculator.calculateExpectedWorkDays(any(), any(), any())).thenReturn(20);

        Attendance attendance = Attendance.builder()
                .internId(10L)
                .workDate(LocalDate.of(2026, 10, 5))
                .status(AttendanceStatus.ON_TIME)
                .totalWorkingHours(4.0)
                .build();

        LeaveRequest halfDayLeave = LeaveRequest.builder()
                .startDate(LocalDate.of(2026, 10, 5))
                .endDate(LocalDate.of(2026, 10, 5))
                .durationType(LeaveDurationType.MORNING)
                .totalDays(0.5)
                .leaveType(LeaveType.SICK)
                .build();

        InternDiligenceReportResponse report = aggregator.calculateInternDiligence(
                intern,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                List.of(attendance),
                List.of(halfDayLeave),
                "Phòng IT",
                "Chương trình Spring Boot"
        );

        assertNotNull(report);
        assertEquals(20, report.getExpectedWorkDays());
        assertEquals(0.5, report.getActualWorkDays());
        assertEquals(0.5, report.getApprovedLeaveDays());
        assertEquals(19.0, report.getUnexcusedAbsenceDays());
        assertEquals(1L, report.getOnTimeCount());
        assertEquals("Phòng IT", report.getDepartmentName());
        assertEquals("Chương trình Spring Boot", report.getProgramTitle());
    }

    @Test
    @DisplayName("Tạo phản hồi KPI rỗng khi không có thực tập sinh nào")
    void buildEmptyKpiResponse_emptyInterns_returnsDefaultStructure() {
        AttendanceReportFilterRequest filter = AttendanceReportFilterRequest.builder()
                .month(10)
                .year(2026)
                .build();

        AttendanceSummaryKpiResponse kpi = aggregator.buildEmptyKpiResponse(
                filter,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)
        );

        assertNotNull(kpi);
        assertEquals(0, kpi.getTotalInterns());
        assertEquals(100.0, kpi.getOverallDiligenceRate());
        assertEquals(0, kpi.getTotalExpectedWorkDays());
        assertEquals(0.0, kpi.getTotalActualWorkDays());
        assertNotNull(kpi.getLeaveReasonBreakdown());
        assertNotNull(kpi.getStatusDistribution());
    }
}
