package org.example.internservice.attendance.report.controller;

import org.example.internservice.attendance.report.dto.request.AttendanceReportFilterRequest;
import org.example.internservice.attendance.report.dto.response.AttendanceSummaryKpiResponse;
import org.example.internservice.attendance.report.dto.response.InternAttendanceDetailReportResponse;
import org.example.internservice.attendance.report.dto.response.InternDiligenceReportResponse;
import org.example.internservice.attendance.report.service.AttendanceReportService;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests - AttendanceReportController")
class AttendanceReportControllerTest {

    @Mock
    private AttendanceReportService attendanceReportService;

    @InjectMocks
    private AttendanceReportController attendanceReportController;

    private Authentication hrAuthentication;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = CustomUserDetails.builder()
                .userId(1L)
                .username("hr_admin")
                .role("HR")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_HR")))
                .build();
        hrAuthentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    @DisplayName("HR lấy Dashboard KPI tổng quan thành công trả về HTTP 200")
    void getAttendanceSummaryKpi_success() {
        AttendanceReportFilterRequest filter = AttendanceReportFilterRequest.builder()
                .month(10)
                .year(2026)
                .build();

        AttendanceSummaryKpiResponse kpiResponse = AttendanceSummaryKpiResponse.builder()
                .totalInterns(20)
                .overallDiligenceRate(92.5)
                .build();

        when(attendanceReportService.getAttendanceSummaryKpi(any(), any())).thenReturn(kpiResponse);

        ResponseEntity<ApiResponse<AttendanceSummaryKpiResponse>> response =
                attendanceReportController.getAttendanceSummaryKpi(filter, hrAuthentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals(20, response.getBody().getData().getTotalInterns());
        verify(attendanceReportService).getAttendanceSummaryKpi(any(), any());
    }

    @Test
    @DisplayName("HR lấy danh sách bảng tổng hợp chuyên cần thành công trả về PageResponse")
    void getInternDiligenceReports_success() {
        AttendanceReportFilterRequest filter = AttendanceReportFilterRequest.builder().month(10).year(2026).build();
        Pageable pageable = PageRequest.of(0, 10);

        InternDiligenceReportResponse item = InternDiligenceReportResponse.builder()
                .internId(1L)
                .internCode("INT-001")
                .fullName("Thực tập sinh A")
                .build();

        PageResponse<InternDiligenceReportResponse> pageResponse = PageResponse.from(new PageImpl<>(List.of(item), pageable, 1));
        when(attendanceReportService.getInternDiligenceReports(any(), any(), any())).thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<InternDiligenceReportResponse>>> response =
                attendanceReportController.getInternDiligenceReports(filter, pageable, hrAuthentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getData().getItems().size());
        verify(attendanceReportService).getInternDiligenceReports(any(), any(), any());
    }

    @Test
    @DisplayName("HR lấy chi tiết bảng công của 1 TTS thành công")
    void getInternAttendanceDetailReport_success() {
        InternAttendanceDetailReportResponse detailResponse = InternAttendanceDetailReportResponse.builder()
                .internId(5L)
                .fullName("Nguyễn Văn B")
                .build();

        when(attendanceReportService.getInternAttendanceDetailReport(eq(5L), eq(10), eq(2026), any()))
                .thenReturn(detailResponse);

        ResponseEntity<ApiResponse<InternAttendanceDetailReportResponse>> response =
                attendanceReportController.getInternAttendanceDetailReport(5L, 10, 2026, hrAuthentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Nguyễn Văn B", response.getBody().getData().getFullName());
        verify(attendanceReportService).getInternAttendanceDetailReport(eq(5L), eq(10), eq(2026), any());
    }

    @Test
    @DisplayName("HR xuất file CSV thành công trả về Content-Disposition attachment và bytes")
    void exportAttendanceReportCsv_success() {
        AttendanceReportFilterRequest filter = AttendanceReportFilterRequest.builder().month(10).year(2026).build();
        byte[] csvData = "Mã TTS,Họ tên\nINT-001,An".getBytes();

        when(attendanceReportService.exportAttendanceReportCsv(any(), any())).thenReturn(csvData);

        ResponseEntity<byte[]> response = attendanceReportController.exportAttendanceReportCsv(filter, hrAuthentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("BaoCaoChuyenCan_InternHub"));
        verify(attendanceReportService).exportAttendanceReportCsv(any(), any());
    }
}
