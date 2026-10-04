package org.example.internservice.attendance.service.impl;

import org.example.internservice.attendance.dto.request.CheckInConfirmRequest;
import org.example.internservice.attendance.dto.request.CheckInInitiateRequest;
import org.example.internservice.attendance.dto.request.CheckInRequest;
import org.example.internservice.attendance.dto.request.CheckOutRequest;
import org.example.internservice.attendance.dto.response.AttendanceResponse;
import org.example.internservice.attendance.dto.response.CheckInQrResponse;
import org.example.internservice.attendance.dto.response.MonthlyAttendanceSummaryResponse;
import org.example.internservice.attendance.dto.response.TodayAttendanceResponse;
import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.OfficeLocation;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.example.internservice.attendance.repository.OfficeLocationRepository;
import org.example.internservice.attendance.service.CheckInQrTokenManager;
import org.example.internservice.attendance.util.FrontendUrlResolver;
import org.example.internservice.attendance.util.HaversineDistanceCalculator;
import org.example.internservice.attendance.util.QrCodeGenerator;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private OfficeLocationRepository officeLocationRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private HaversineDistanceCalculator haversineCalculator;

    @Mock
    private CheckInQrTokenManager qrTokenManager;

    @Mock
    private QrCodeGenerator qrCodeGenerator;

    @Mock
    private FrontendUrlResolver frontendUrlResolver;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    private InternProfile mockIntern;
    private OfficeLocation mockOffice;

    @BeforeEach
    void setUp() {
        mockIntern = InternProfile.builder()
                .userId(10L)
                .internCode("INT-2026-001")
                .fullName("Nguyen Van A")
                .email("intern.a@internhub.com")
                .phone("0901234567")
                .status(InternStatus.INTERNING)
                .build();
        mockIntern.setId(1L);

        mockOffice = OfficeLocation.builder()
                .name("Trụ sở chính InternHub")
                .latitude(21.028511)
                .longitude(105.854444)
                .allowedRadiusMeters(25.0)
                .isActive(true)
                .build();
        mockOffice.setId(1L);
    }

    @Test
    @DisplayName("Check-in thành công khi vị trí nằm trong bán kính cho phép")
    void checkIn_whenValid_shouldSaveAndReturnResponse() {
        CheckInRequest request = CheckInRequest.builder()
                .latitude(21.028515)
                .longitude(105.854440)
                .notes("Đến văn phòng đúng giờ")
                .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.existsByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(false);
        when(officeLocationRepository.findFirstByIsActiveTrueOrderByIdAsc()).thenReturn(Optional.of(mockOffice));
        when(haversineCalculator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5.2);

        Attendance savedAttendance = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.now(AttendanceServiceImpl.VIETNAM_ZONE))
                .checkInTime(LocalDateTime.now(AttendanceServiceImpl.VIETNAM_ZONE))
                .checkInLatitude(request.getLatitude())
                .checkInLongitude(request.getLongitude())
                .checkInDistance(5.2)
                .status(AttendanceStatus.ON_TIME)
                .notes(request.getNotes())
                .build();
        savedAttendance.setId(100L);

        when(attendanceRepository.save(any(Attendance.class))).thenReturn(savedAttendance);

        AttendanceResponse response = attendanceService.checkIn(10L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getInternId());
        assertEquals(5.2, response.getCheckInDistance());
        verify(attendanceRepository).save(any(Attendance.class));
    }

    @Test
    @DisplayName("Check-in ném DuplicateResourceException khi đã check-in trong ngày")
    void checkIn_whenAlreadyCheckedIn_shouldThrowDuplicateResourceException() {
        CheckInRequest request = CheckInRequest.builder()
                .latitude(21.028515)
                .longitude(105.854440)
                .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.existsByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> attendanceService.checkIn(10L, request));
    }

    @Test
    @DisplayName("Check-in ném BadRequestException khi tọa độ vượt quá bán kính 25m")
    void checkIn_whenBeyondRadius_shouldThrowBadRequestException() {
        CheckInRequest request = CheckInRequest.builder()
                .latitude(21.035000)
                .longitude(105.854440)
                .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.existsByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(false);
        when(officeLocationRepository.findFirstByIsActiveTrueOrderByIdAsc()).thenReturn(Optional.of(mockOffice));
        when(haversineCalculator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(65.4);

        assertThrows(BadRequestException.class, () -> attendanceService.checkIn(10L, request));
    }

    @Test
    @DisplayName("Check-out ném BadRequestException khi chưa check-in trong ngày")
    void checkOut_whenNotCheckedIn_shouldThrowBadRequestException() {
        CheckOutRequest request = CheckOutRequest.builder()
                .latitude(21.028515)
                .longitude(105.854440)
                .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.findByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> attendanceService.checkOut(10L, request));
    }

    @Test
    @DisplayName("Check-out ném BadRequestException khi đã check-out hoàn tất trước đó")
    void checkOut_whenAlreadyCheckedOut_shouldThrowBadRequestException() {
        CheckOutRequest request = CheckOutRequest.builder()
                .latitude(21.028515)
                .longitude(105.854440)
                .build();

        Attendance existingAttendance = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.now(AttendanceServiceImpl.VIETNAM_ZONE))
                .checkInTime(LocalDateTime.now(AttendanceServiceImpl.VIETNAM_ZONE).minusHours(8))
                .checkOutTime(LocalDateTime.now(AttendanceServiceImpl.VIETNAM_ZONE))
                .status(AttendanceStatus.ON_TIME)
                .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.findByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(Optional.of(existingAttendance));

        assertThrows(BadRequestException.class, () -> attendanceService.checkOut(10L, request));
    }

    @Test
    @DisplayName("Check-out thành công tính tổng giờ và cập nhật bản ghi")
    void checkOut_whenValid_shouldCalculateHoursAndSave() {
        CheckOutRequest request = CheckOutRequest.builder()
                .latitude(21.028515)
                .longitude(105.854440)
                .notes("Kết thúc ngày")
                .build();

        LocalDateTime checkIn = LocalDateTime.now(AttendanceServiceImpl.VIETNAM_ZONE).minusHours(8);
        Attendance existingAttendance = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.now(AttendanceServiceImpl.VIETNAM_ZONE))
                .checkInTime(checkIn)
                .checkInDistance(3.0)
                .status(AttendanceStatus.ON_TIME)
                .build();
        existingAttendance.setId(100L);

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.findByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(Optional.of(existingAttendance));
        when(officeLocationRepository.findFirstByIsActiveTrueOrderByIdAsc()).thenReturn(Optional.of(mockOffice));
        when(haversineCalculator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(4.1);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceResponse response = attendanceService.checkOut(10L, request);

        assertNotNull(response);
        assertNotNull(response.getCheckOutTime());
        assertNotNull(response.getTotalWorkingHours());
        assertTrue(response.getTotalWorkingHours() >= 7.9);
        assertEquals(4.1, response.getCheckOutDistance());
        verify(attendanceRepository).save(any(Attendance.class));
    }

    @Test
    @DisplayName("Lấy trạng thái hôm nay trả về chưa check-in khi chưa có bản ghi")
    void getTodayAttendance_whenNotCheckedIn_shouldReturnUncheckedState() {
        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.findByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(officeLocationRepository.findFirstByIsActiveTrueOrderByIdAsc()).thenReturn(Optional.of(mockOffice));

        TodayAttendanceResponse response = attendanceService.getTodayAttendance(10L);

        assertNotNull(response);
        assertFalse(response.isHasCheckedIn());
        assertFalse(response.isHasCheckedOut());
        assertEquals("Trụ sở chính InternHub", response.getOfficeName());
    }

    @Test
    @DisplayName("Lấy lịch sử chấm công theo tháng tổng hợp chính xác số ngày và giờ công")
    void getMyHistory_shouldAggregateMonthlyStats() {
        Attendance att1 = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.of(2026, 9, 1))
                .checkInTime(LocalDateTime.of(2026, 9, 1, 8, 5))
                .checkOutTime(LocalDateTime.of(2026, 9, 1, 17, 30))
                .totalWorkingHours(9.4)
                .status(AttendanceStatus.ON_TIME)
                .build();

        Attendance att2 = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.of(2026, 9, 2))
                .checkInTime(LocalDateTime.of(2026, 9, 2, 8, 30))
                .checkOutTime(LocalDateTime.of(2026, 9, 2, 17, 35))
                .totalWorkingHours(9.0)
                .status(AttendanceStatus.LATE)
                .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.findByInternIdAndWorkDateBetweenOrderByWorkDateAsc(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(att1, att2));

        MonthlyAttendanceSummaryResponse summary = attendanceService.getMyHistory(10L, 9, 2026);

        assertNotNull(summary);
        assertEquals(2, summary.getTotalWorkingDays());
        assertEquals(1, summary.getOnTimeDays());
        assertEquals(1, summary.getLateDays());
        assertEquals(18.4, summary.getTotalWorkingHours());
        assertEquals(2, summary.getAttendances().size());
    }

    @Test
    @DisplayName("Khởi tạo Check-in trong bán kính hợp lệ sinh mã QR thành công")
    void initiateCheckIn_validDistance_shouldReturnQrResponse() {
        CheckInInitiateRequest request = CheckInInitiateRequest.builder()
                .latitude(21.035665)
                .longitude(105.768296)
                .build();

        CheckInQrTokenManager.CheckInSession session = CheckInQrTokenManager.CheckInSession.builder()
                .token("mock-uuid-token")
                .internId(1L)
                .userId(10L)
                .workDate(LocalDate.now())
                .latitude(21.035665)
                .longitude(105.768296)
                .distance(5.0)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(60))
                .consumed(false)
                .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.existsByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(false);
        when(officeLocationRepository.findFirstByIsActiveTrueOrderByIdAsc()).thenReturn(Optional.of(mockOffice));
        when(haversineCalculator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5.0);
        when(qrTokenManager.createSession(eq(1L), eq(10L), any(LocalDate.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(session);
        when(frontendUrlResolver.resolveBaseUrl(any())).thenReturn("http://localhost:5173");
        when(frontendUrlResolver.buildConfirmationUrl("http://localhost:5173", "mock-uuid-token"))
                .thenReturn("http://localhost:5173/intern/attendance/confirm?token=mock-uuid-token");
        when(qrCodeGenerator.generateQrCodeDataUrl("http://localhost:5173/intern/attendance/confirm?token=mock-uuid-token"))
                .thenReturn("data:image/png;base64,mockQrCode");

        CheckInQrResponse response = attendanceService.initiateCheckIn(10L, request);

        assertNotNull(response);
        assertEquals("mock-uuid-token", response.getQrToken());
        assertEquals("http://localhost:5173/intern/attendance/confirm?token=mock-uuid-token", response.getConfirmationUrl());
        assertEquals("data:image/png;base64,mockQrCode", response.getQrCodeDataUrl());
        assertEquals(60, response.getExpiresInSeconds());
        assertEquals(5.0, response.getDistance());
    }

    @Test
    @DisplayName("Khởi tạo Check-in với clientBaseUrl tùy chỉnh (LAN IP) thành công")
    void initiateCheckIn_withCustomClientBaseUrl_shouldUseCustomUrl() {
        CheckInInitiateRequest request = CheckInInitiateRequest.builder()
                .latitude(21.028511)
                .longitude(105.854444)
                .clientBaseUrl("http://192.168.1.15:5173")
                .build();

        CheckInQrTokenManager.CheckInSession session = CheckInQrTokenManager.CheckInSession.builder()
                .token("lan-uuid-token")
                .internId(1L)
                .userId(10L)
                .workDate(LocalDate.now())
                .latitude(21.028511)
                .longitude(105.854444)
                .distance(3.0)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(60))
                .consumed(false)
                .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.existsByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(false);
        when(officeLocationRepository.findFirstByIsActiveTrueOrderByIdAsc()).thenReturn(Optional.of(mockOffice));
        when(haversineCalculator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(3.0);
        when(qrTokenManager.createSession(eq(1L), eq(10L), any(LocalDate.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(session);
        when(frontendUrlResolver.resolveBaseUrl("http://192.168.1.15:5173")).thenReturn("http://192.168.1.15:5173");
        when(frontendUrlResolver.buildConfirmationUrl("http://192.168.1.15:5173", "lan-uuid-token"))
                .thenReturn("http://192.168.1.15:5173/intern/attendance/confirm?token=lan-uuid-token");
        when(qrCodeGenerator.generateQrCodeDataUrl("http://192.168.1.15:5173/intern/attendance/confirm?token=lan-uuid-token"))
                .thenReturn("data:image/png;base64,lanQrCode");

        CheckInQrResponse response = attendanceService.initiateCheckIn(10L, request);

        assertNotNull(response);
        assertEquals("lan-uuid-token", response.getQrToken());
        assertEquals("http://192.168.1.15:5173/intern/attendance/confirm?token=lan-uuid-token", response.getConfirmationUrl());
        assertEquals("data:image/png;base64,lanQrCode", response.getQrCodeDataUrl());
    }

    @Test
    @DisplayName("Xác nhận Check-in với QR token hợp lệ thành công")
    void confirmCheckIn_validToken_shouldSaveAttendance() {
        CheckInConfirmRequest request = CheckInConfirmRequest.builder()
                .qrToken("valid-token")
                .notes("Vào ca")
                .build();

        CheckInQrTokenManager.CheckInSession session = CheckInQrTokenManager.CheckInSession.builder()
                .token("valid-token")
                .internId(1L)
                .userId(10L)
                .workDate(LocalDate.now())
                .latitude(21.035665)
                .longitude(105.768296)
                .distance(8.0)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(60))
                .consumed(false)
                .build();

        Attendance savedAttendance = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.now())
                .checkInTime(LocalDateTime.now())
                .checkInDistance(8.0)
                .status(AttendanceStatus.ON_TIME)
                .notes("Vào ca")
                .build();
        savedAttendance.setId(99L);

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.existsByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(false);
        when(qrTokenManager.validateAndConsume("valid-token", 1L)).thenReturn(session);
        when(attendanceRepository.save(any(Attendance.class))).thenReturn(savedAttendance);

        AttendanceResponse response = attendanceService.confirmCheckIn(10L, request);

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals(1L, response.getInternId());
        verify(attendanceRepository).save(any(Attendance.class));
    }
}
