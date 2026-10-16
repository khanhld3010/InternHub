package org.example.internservice.attendance.report.service.impl;

import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.report.dto.request.AttendanceReportFilterRequest;
import org.example.internservice.attendance.report.dto.response.AttendanceSummaryKpiResponse;
import org.example.internservice.attendance.report.dto.response.InternAttendanceDetailReportResponse;
import org.example.internservice.attendance.report.dto.response.InternDiligenceReportResponse;
import org.example.internservice.attendance.report.util.AttendanceCsvExporter;
import org.example.internservice.attendance.report.util.AttendanceReportAggregator;
import org.example.internservice.attendance.report.util.WorkDayCalculator;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.leave.repository.LeaveRequestRepository;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests - AttendanceReportServiceImpl")
class AttendanceReportServiceImplTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private WorkDayCalculator workDayCalculator;

    @Mock
    private AttendanceCsvExporter csvExporter;

    @Mock
    private org.example.internservice.intern.service.MentorAccessHelper mentorAccessHelper;

    private AttendanceReportAggregator reportAggregator;
    private AttendanceReportServiceImpl attendanceReportService;

    private InternProfile intern;
    private CustomUserDetails hrUser;
    private CustomUserDetails mentorUser;

    @BeforeEach
    void setUp() {
        reportAggregator = new AttendanceReportAggregator(workDayCalculator);
        attendanceReportService = new AttendanceReportServiceImpl(
                attendanceRepository,
                leaveRequestRepository,
                internProfileRepository,
                reportAggregator,
                csvExporter,
                mentorAccessHelper
        );
        intern = InternProfile.builder()
                .userId(101L)
                .internCode("INT-2026-0001")
                .fullName("Nguyễn Văn An")
                .email("an.nguyen@example.com")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .mentorId(99L)
                .mentorName("Trần Mentor")
                .status(InternStatus.INTERNING)
                .build();
        intern.setId(1L);

        hrUser = CustomUserDetails.builder()
                .userId(10L)
                .username("hr_user")
                .role("HR")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_HR")))
                .build();

        mentorUser = CustomUserDetails.builder()
                .userId(99L)
                .username("mentor_user")
                .role("MENTOR")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))
                .build();
    }

    @Test
    @DisplayName("Lấy báo cáo KPI tổng quan thành công")
    void getAttendanceSummaryKpi_validFilter_returnsSummaryKpi() {
        AttendanceReportFilterRequest filter = AttendanceReportFilterRequest.builder()
                .month(10)
                .year(2026)
                .build();

        when(internProfileRepository.findAll(any(Specification.class))).thenReturn(List.of(intern));
        when(workDayCalculator.calculateExpectedWorkDays(any(), any(), any())).thenReturn(22);

        Attendance attendance = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.of(2026, 10, 1))
                .status(AttendanceStatus.ON_TIME)
                .totalWorkingHours(8.0)
                .build();

        when(attendanceRepository.findByInternIdInAndWorkDateBetweenOrderByWorkDateAsc(any(), any(), any()))
                .thenReturn(List.of(attendance));
        when(leaveRequestRepository.findApprovedLeavesByInternIdsAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AttendanceSummaryKpiResponse response = attendanceReportService.getAttendanceSummaryKpi(filter, hrUser);

        assertNotNull(response);
        assertEquals(1, response.getTotalInterns());
        assertEquals(1L, response.getTotalOnTimeCount());
        assertEquals(1.0, response.getTotalActualWorkDays());
    }

    @Test
    @DisplayName("Lấy danh sách báo cáo chuyên cần phân trang thành công")
    void getInternDiligenceReports_validFilter_returnsPaginatedReports() {
        AttendanceReportFilterRequest filter = AttendanceReportFilterRequest.builder()
                .month(10)
                .year(2026)
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        when(internProfileRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(intern), pageable, 1));
        when(workDayCalculator.calculateExpectedWorkDays(any(), any(), any())).thenReturn(22);

        Attendance attendance = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.of(2026, 10, 1))
                .status(AttendanceStatus.ON_TIME)
                .totalWorkingHours(8.0)
                .build();

        when(attendanceRepository.findByInternIdInAndWorkDateBetweenOrderByWorkDateAsc(any(), any(), any()))
                .thenReturn(List.of(attendance));
        when(leaveRequestRepository.findApprovedLeavesByInternIdsAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        PageResponse<InternDiligenceReportResponse> response = attendanceReportService.getInternDiligenceReports(filter, pageable, hrUser);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals("INT-2026-0001", response.getItems().get(0).getInternCode());
    }

    @Test
    @DisplayName("Lấy danh sách báo cáo chuyên cần có lọc theo xếp loại diligenceStatus thành công")
    void getInternDiligenceReports_withDiligenceStatusFilter_returnsAccuratelyFilteredAndPaged() {
        AttendanceReportFilterRequest filter = AttendanceReportFilterRequest.builder()
                .month(10)
                .year(2026)
                .diligenceStatus(org.example.internservice.attendance.report.enums.DiligenceStatus.CRITICAL)
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        when(internProfileRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(intern));
        when(workDayCalculator.calculateExpectedWorkDays(any(), any(), any())).thenReturn(22);

        // Không có chấm công -> unexcusedAbsence = 22 -> CRITICAL
        when(attendanceRepository.findByInternIdInAndWorkDateBetweenOrderByWorkDateAsc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leaveRequestRepository.findApprovedLeavesByInternIdsAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        PageResponse<InternDiligenceReportResponse> response = attendanceReportService.getInternDiligenceReports(filter, pageable, hrUser);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(org.example.internservice.attendance.report.enums.DiligenceStatus.CRITICAL, response.getItems().get(0).getDiligenceStatus());
        assertEquals(1L, response.getTotalItems());
    }

    @Test
    @DisplayName("Mentor không được xem bảng công của TTS không do mình quản lý")
    void getInternAttendanceDetailReport_mentorNotAssigned_throwsAccessDenied() {
        CustomUserDetails otherMentor = CustomUserDetails.builder()
                .userId(888L)
                .username("other_mentor")
                .role("MENTOR")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))
                .build();

        when(internProfileRepository.findById(1L)).thenReturn(Optional.of(intern));
        when(mentorAccessHelper.isInternManagedByMentor(eq(intern), eq(otherMentor))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                attendanceReportService.getInternAttendanceDetailReport(1L, 10, 2026, otherMentor));
    }

    @Test
    @DisplayName("Mentor xem chi tiết bảng công của TTS do mình phụ trách thành công")
    void getInternAttendanceDetailReport_assignedMentor_success() {
        when(internProfileRepository.findById(1L)).thenReturn(Optional.of(intern));
        when(mentorAccessHelper.isInternManagedByMentor(eq(intern), eq(mentorUser))).thenReturn(true);
        when(workDayCalculator.calculateExpectedWorkDays(any(), any(), any())).thenReturn(22);
        when(workDayCalculator.getDayOfWeekVietnamese(any())).thenReturn("Thứ Năm");
        when(attendanceRepository.findByInternIdAndWorkDateBetweenOrderByWorkDateAsc(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leaveRequestRepository.findApprovedLeavesByInternIdAndDateRange(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        InternAttendanceDetailReportResponse response =
                attendanceReportService.getInternAttendanceDetailReport(1L, 10, 2026, mentorUser);

        assertNotNull(response);
        assertEquals(1L, response.getInternId());
    }

    @Test
    @DisplayName("HR xem chi tiết bảng công theo từng ngày của thực tập sinh thành công")
    void getInternAttendanceDetailReport_hrRole_returnsDetailItems() {
        when(internProfileRepository.findById(1L)).thenReturn(Optional.of(intern));
        when(workDayCalculator.calculateExpectedWorkDays(any(), any(), any())).thenReturn(22);
        when(workDayCalculator.getDayOfWeekVietnamese(any())).thenReturn("Thứ Năm");

        Attendance attendance = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.of(2026, 10, 1))
                .checkInTime(LocalDateTime.of(2026, 10, 1, 8, 0))
                .checkOutTime(LocalDateTime.of(2026, 10, 1, 17, 30))
                .totalWorkingHours(8.5)
                .status(AttendanceStatus.ON_TIME)
                .build();

        when(attendanceRepository.findByInternIdAndWorkDateBetweenOrderByWorkDateAsc(eq(1L), any(), any()))
                .thenReturn(List.of(attendance));
        when(leaveRequestRepository.findApprovedLeavesByInternIdAndDateRange(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        InternAttendanceDetailReportResponse response = attendanceReportService.getInternAttendanceDetailReport(1L, 10, 2026, hrUser);

        assertNotNull(response);
        assertEquals(1L, response.getInternId());
        assertEquals("Nguyễn Văn An", response.getFullName());
        assertNotNull(response.getDailyDetails());
    }

    @Test
    @DisplayName("Xuất báo cáo chuyên cần sang mảng byte CSV thành công")
    void exportAttendanceReportCsv_validFilter_returnsCsvBytes() {
        AttendanceReportFilterRequest filter = AttendanceReportFilterRequest.builder()
                .month(10)
                .year(2026)
                .build();

        when(internProfileRepository.findAll(any(Specification.class))).thenReturn(List.of(intern));
        when(workDayCalculator.calculateExpectedWorkDays(any(), any(), any())).thenReturn(22);
        when(csvExporter.exportToCsv(any())).thenReturn(new byte[]{1, 2, 3});

        byte[] result = attendanceReportService.exportAttendanceReportCsv(filter, hrUser);

        assertNotNull(result);
        assertEquals(3, result.length);
    }
}
