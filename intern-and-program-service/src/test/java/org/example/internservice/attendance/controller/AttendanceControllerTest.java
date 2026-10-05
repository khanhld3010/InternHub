package org.example.internservice.attendance.controller;

import org.example.internservice.attendance.dto.request.CheckInConfirmRequest;
import org.example.internservice.attendance.dto.request.CheckInInitiateRequest;
import org.example.internservice.attendance.dto.request.CheckInRequest;
import org.example.internservice.attendance.dto.request.CheckOutRequest;
import org.example.internservice.attendance.dto.response.AttendanceResponse;
import org.example.internservice.attendance.dto.response.CheckInQrResponse;
import org.example.internservice.attendance.dto.response.MonthlyAttendanceSummaryResponse;
import org.example.internservice.attendance.dto.response.TodayAttendanceResponse;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.service.AttendanceService;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock
    private AttendanceService attendanceService;

    @InjectMocks
    private AttendanceController attendanceController;

    private Authentication mockAuthentication;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = CustomUserDetails.builder()
                .userId(10L)
                .username("intern_test")
                .role("INTERN")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERN")))
                .build();
        mockAuthentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    @DisplayName("API /today trả về HTTP 200 kèm trạng thái hôm nay")
    void getTodayAttendance_shouldReturn200() {
        TodayAttendanceResponse mockToday = TodayAttendanceResponse.builder()
                .workDate(LocalDate.now())
                .hasCheckedIn(true)
                .hasCheckedOut(false)
                .status(AttendanceStatus.ON_TIME)
                .officeName("Trụ sở chính")
                .build();

        when(attendanceService.getTodayAttendance(10L)).thenReturn(mockToday);

        ResponseEntity<ApiResponse<TodayAttendanceResponse>> responseEntity =
                attendanceController.getTodayAttendance(mockAuthentication);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals("Lấy trạng thái chấm công hôm nay thành công", responseEntity.getBody().getMessage());
        assertEquals(mockToday, responseEntity.getBody().getData());
        verify(attendanceService).getTodayAttendance(10L);
    }

    @Test
    @DisplayName("API /check-in trả về HTTP 201 Created khi thành công")
    void checkIn_shouldReturn201() {
        CheckInRequest request = CheckInRequest.builder()
                .latitude(21.028511)
                .longitude(105.854444)
                .notes("Check-in đúng giờ")
                .build();

        AttendanceResponse mockResponse = AttendanceResponse.builder()
                .id(1L)
                .internId(1L)
                .workDate(LocalDate.now())
                .checkInTime(LocalDateTime.now())
                .status(AttendanceStatus.ON_TIME)
                .build();

        when(attendanceService.checkIn(eq(10L), any(CheckInRequest.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<AttendanceResponse>> responseEntity =
                attendanceController.checkIn(request, mockAuthentication);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals("Check-in thành công", responseEntity.getBody().getMessage());
        assertEquals(mockResponse, responseEntity.getBody().getData());
        verify(attendanceService).checkIn(eq(10L), any(CheckInRequest.class));
    }

    @Test
    @DisplayName("API /check-out trả về HTTP 200 OK khi thành công")
    void checkOut_shouldReturn200() {
        CheckOutRequest request = CheckOutRequest.builder()
                .latitude(21.028511)
                .longitude(105.854444)
                .notes("Check-out kết thúc ngày")
                .build();

        AttendanceResponse mockResponse = AttendanceResponse.builder()
                .id(1L)
                .internId(1L)
                .workDate(LocalDate.now())
                .checkOutTime(LocalDateTime.now())
                .totalWorkingHours(9.5)
                .status(AttendanceStatus.ON_TIME)
                .build();

        when(attendanceService.checkOut(eq(10L), any(CheckOutRequest.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<AttendanceResponse>> responseEntity =
                attendanceController.checkOut(request, mockAuthentication);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals("Check-out thành công", responseEntity.getBody().getMessage());
        assertEquals(mockResponse, responseEntity.getBody().getData());
        verify(attendanceService).checkOut(eq(10L), any(CheckOutRequest.class));
    }

    @Test
    @DisplayName("API /my-history trả về HTTP 200 OK kèm tổng hợp theo tháng")
    void getMyHistory_shouldReturn200() {
        MonthlyAttendanceSummaryResponse mockSummary = MonthlyAttendanceSummaryResponse.builder()
                .month(9)
                .year(2026)
                .totalWorkingDays(20)
                .onTimeDays(19)
                .lateDays(1)
                .earlyLeaveDays(0)
                .totalWorkingHours(170.0)
                .attendances(List.of())
                .build();

        when(attendanceService.getMyHistory(10L, 9, 2026)).thenReturn(mockSummary);

        ResponseEntity<ApiResponse<MonthlyAttendanceSummaryResponse>> responseEntity =
                attendanceController.getMyHistory(9, 2026, mockAuthentication);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(mockSummary, responseEntity.getBody().getData());
        verify(attendanceService).getMyHistory(10L, 9, 2026);
    }

    @Test
    @DisplayName("API /check-in/initiate trả về HTTP 200 kèm mã QR")
    void initiateCheckIn_shouldReturn200AndQr() {
        CheckInInitiateRequest request = CheckInInitiateRequest.builder()
                .latitude(21.035665)
                .longitude(105.768296)
                .build();

        CheckInQrResponse mockQr = CheckInQrResponse.builder()
                .qrToken("test-token")
                .qrCodeDataUrl("data:image/png;base64,...")
                .expiresInSeconds(60)
                .distance(6.2)
                .officeName("Trụ sở chính InternHub")
                .message("Khoảng cách hợp lệ")
                .build();

        when(attendanceService.initiateCheckIn(eq(10L), any(CheckInInitiateRequest.class))).thenReturn(mockQr);

        ResponseEntity<ApiResponse<CheckInQrResponse>> responseEntity =
                attendanceController.initiateCheckIn(request, mockAuthentication);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(mockQr, responseEntity.getBody().getData());
        verify(attendanceService).initiateCheckIn(eq(10L), any(CheckInInitiateRequest.class));
    }

    @Test
    @DisplayName("API /check-in/confirm trả về HTTP 201 Created khi xác nhận thành công")
    void confirmCheckIn_shouldReturn201() {
        CheckInConfirmRequest request = CheckInConfirmRequest.builder()
                .qrToken("test-token")
                .notes("Đúng giờ")
                .build();

        AttendanceResponse mockResponse = AttendanceResponse.builder()
                .id(101L)
                .internId(1L)
                .workDate(LocalDate.now())
                .checkInTime(LocalDateTime.now())
                .status(AttendanceStatus.ON_TIME)
                .build();

        when(attendanceService.confirmCheckIn(eq(10L), any(CheckInConfirmRequest.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<AttendanceResponse>> responseEntity =
                attendanceController.confirmCheckIn(request, mockAuthentication);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals("Check-in thành công", responseEntity.getBody().getMessage());
        assertEquals(mockResponse, responseEntity.getBody().getData());
        verify(attendanceService).confirmCheckIn(eq(10L), any(CheckInConfirmRequest.class));
    }
}
