package org.example.internservice.attendance.service.impl;

import org.example.internservice.allowance.repository.InternAllowanceRecordRepository;
import org.example.internservice.attendance.dto.request.AttendanceAppealRequest;
import org.example.internservice.attendance.dto.request.CheckInConfirmRequest;
import org.example.internservice.attendance.dto.request.CheckInInitiateRequest;
import org.example.internservice.attendance.dto.request.CheckOutRequest;
import org.example.internservice.attendance.dto.request.ConfirmForgottenCheckoutRequest;
import org.example.internservice.attendance.dto.request.ReviewAttendanceRequest;
import org.example.internservice.attendance.dto.response.AttendanceResponse;
import org.example.internservice.attendance.dto.response.CheckInQrResponse;
import org.example.internservice.attendance.dto.response.MonthlyAttendanceSummaryResponse;
import org.example.internservice.attendance.dto.response.TodayAttendanceResponse;
import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.OfficeLocation;
import org.example.internservice.attendance.entity.enums.AttendanceReviewStatus;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.repository.AttendanceAuditLogRepository;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.example.internservice.attendance.repository.InternDeviceRepository;
import org.example.internservice.attendance.repository.OfficeLocationRepository;
import org.example.internservice.attendance.service.CheckInQrTokenManager;
import org.example.internservice.attendance.service.SseEmitterRegistry;
import org.example.internservice.attendance.service.TrustLevelEvaluator;
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
import java.time.LocalTime;
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

    @Mock
    private TrustLevelEvaluator trustLevelEvaluator;

    @Mock
    private AttendanceAuditLogRepository auditLogRepository;

    @Mock
    private InternDeviceRepository internDeviceRepository;

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    @Mock
    private InternAllowanceRecordRepository allowanceRecordRepository;

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

        TrustLevelEvaluator.EvaluationResult evalResult =
                TrustLevelEvaluator.EvaluationResult.builder()
                        .trustLevel(org.example.internservice.attendance.entity.enums.TrustLevel.HIGH)
                        .reviewStatus(org.example.internservice.attendance.entity.enums.AttendanceReviewStatus.AUTO_APPROVED)
                        .auditFlags(List.of("TRUSTED_DEVICE"))
                        .calculatedDistance(8.0)
                        .isCompanyIp(true)
                        .isKnownDevice(true)
                        .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.existsByInternIdAndWorkDate(eq(1L), any(LocalDate.class))).thenReturn(false);
        when(qrTokenManager.validateAndConsume("valid-token", 1L)).thenReturn(session);
        when(officeLocationRepository.findFirstByIsActiveTrueOrderByIdAsc()).thenReturn(Optional.of(mockOffice));
        when(trustLevelEvaluator.evaluate(eq(1L), any(), any(), any(), any(), any(), any())).thenReturn(evalResult);
        when(attendanceRepository.save(any(Attendance.class))).thenReturn(savedAttendance);

        AttendanceResponse response = attendanceService.confirmCheckIn(10L, request, "192.168.1.50");

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals(1L, response.getInternId());
        verify(attendanceRepository).save(any(Attendance.class));
        verify(auditLogRepository).save(any());
        verify(sseEmitterRegistry).emitCheckInSuccess(eq("valid-token"), any());
    }

    @Test
    @DisplayName("Thực tập sinh gửi giải trình chấm công khi bị từ chối hoặc cần duyệt lại thành công")
    void appealAttendance_validRequest_shouldSetPendingReviewAndSaveAudit() {
        Attendance attendance = Attendance.builder()
                .internId(1L)
                .workDate(LocalDate.now())
                .checkInTime(LocalDateTime.now())
                .status(AttendanceStatus.LATE)
                .reviewStatus(org.example.internservice.attendance.entity.enums.AttendanceReviewStatus.PENDING_REVIEW)
                .build();
        attendance.setId(50L);

        org.example.internservice.attendance.dto.request.AttendanceAppealRequest request =
                org.example.internservice.attendance.dto.request.AttendanceAppealRequest.builder()
                        .reason("GPS trôi do làm việc tầng hầm B1, kèm ảnh chụp thẻ từ")
                        .evidenceUrl("https://storage.internhub.com/appeals/evidence-50.png")
                        .build();

        when(internProfileRepository.findByUserId(10L)).thenReturn(Optional.of(mockIntern));
        when(attendanceRepository.findById(50L)).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceResponse response = attendanceService.appealAttendance(10L, 50L, request);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals("PENDING_REVIEW", response.getReviewStatus());
        assertTrue(attendance.getNotes().contains("GPS trôi do làm việc tầng hầm B1"));
        verify(attendanceRepository).save(any(Attendance.class));
    }

    @Test
    @DisplayName("Mentor hoặc HR xác nhận giờ ra thực tế cho ca quên check-out thành công và cập nhật lại status")
    void confirmForgottenCheckout_validActualTime_shouldUpdateWorkingHoursAndStatus() {
        LocalDate workDate = LocalDate.of(2026, 10, 16);
        LocalDateTime checkIn = LocalDateTime.of(workDate, LocalTime.of(8, 0));
        Attendance attendance = Attendance.builder()
                .internId(1L)
                .workDate(workDate)
                .checkInTime(checkIn)
                .status(AttendanceStatus.FORGOTTEN_CHECKOUT)
                .build();
        attendance.setId(77L);

        ConfirmForgottenCheckoutRequest request = ConfirmForgottenCheckoutRequest.builder()
                .actualCheckOutTime(LocalTime.of(17, 30))
                .confirmationNote("Thực tập sinh xác nhận ra về đúng 17:30 cùng team")
                .build();

        when(attendanceRepository.findById(77L)).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceResponse response = attendanceService.confirmForgottenCheckout(77L, request, "mentor_quang");

        assertNotNull(response);
        assertEquals(77L, response.getId());
        assertEquals(AttendanceStatus.ON_TIME, attendance.getStatus());
        assertEquals(9.5, attendance.getTotalWorkingHours());
        assertEquals(AttendanceReviewStatus.HR_APPROVED, attendance.getReviewStatus());
        assertEquals("mentor_quang", attendance.getReviewedBy());
        assertTrue(attendance.getNotes().contains("mentor_quang"));
        verify(attendanceRepository).save(any(Attendance.class));
    }

    @Test
    @DisplayName("Khóa sổ kỳ công: Ném BadRequestException khi cố tình sửa kết quả chấm công thuộc kỳ phụ cấp đã APPROVED hoặc PAID")
    void reviewAttendance_whenAllowancePeriodLocked_shouldThrowBadRequestException() {
        LocalDate workDate = LocalDate.of(2026, 9, 15);
        Attendance attendance = Attendance.builder()
                .internId(10L)
                .workDate(workDate)
                .status(AttendanceStatus.LATE)
                .build();
        attendance.setId(88L);

        ReviewAttendanceRequest request = ReviewAttendanceRequest.builder()
                .reviewStatus(AttendanceReviewStatus.HR_APPROVED)
                .reviewNote("Sửa chấm công tháng cũ")
                .build();

        when(attendanceRepository.findById(88L)).thenReturn(Optional.of(attendance));
        when(allowanceRecordRepository.existsByInternProfileIdAndPeriodMonthAndStatusIn(
                eq(10L), eq("2026-09"), any())).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                attendanceService.reviewAttendance(88L, request, "hr_admin"));

        assertTrue(ex.getMessage().contains("đã được phê duyệt hoặc chi trả"));
        assertTrue(ex.getMessage().contains("đã bị khóa sổ"));
    }

    @Test
    @DisplayName("Khóa sổ kỳ công: Cập nhật chấm công thành công khi kỳ phụ cấp chưa khóa (ở trạng thái DRAFT hoặc chưa tạo)")
    void reviewAttendance_whenAllowancePeriodNotLocked_shouldSucceed() {
        LocalDate workDate = LocalDate.of(2026, 10, 15);
        Attendance attendance = Attendance.builder()
                .internId(10L)
                .workDate(workDate)
                .status(AttendanceStatus.LATE)
                .build();
        attendance.setId(89L);

        ReviewAttendanceRequest request = ReviewAttendanceRequest.builder()
                .reviewStatus(AttendanceReviewStatus.HR_APPROVED)
                .reviewNote("Duyệt giải trình hợp lệ")
                .build();

        when(attendanceRepository.findById(89L)).thenReturn(Optional.of(attendance));
        when(allowanceRecordRepository.existsByInternProfileIdAndPeriodMonthAndStatusIn(
                eq(10L), eq("2026-10"), any())).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceResponse response = attendanceService.reviewAttendance(89L, request, "hr_admin");

        assertNotNull(response);
        assertEquals(89L, response.getId());
        assertEquals("HR_APPROVED", response.getReviewStatus());
        verify(attendanceRepository).save(any(Attendance.class));
    }

    @Test
    @DisplayName("Lỗi 36 [P0]: Thực tập sinh có trạng thái không phải INTERNING (COMPLETED, ON_HOLD, PENDING...) bị chặn thao tác chấm công")
    void checkIn_whenInternNotInterning_shouldThrowBadRequestException() {
        CheckInInitiateRequest request = CheckInInitiateRequest.builder()
                .latitude(21.028515)
                .longitude(105.854440)
                .clientBaseUrl("https://internhub.example.com")
                .build();

        InternProfile completedIntern = InternProfile.builder()
                .userId(20L)
                .internCode("INT-2026-COMP")
                .fullName("Nguyen Van Hoan Thanh")
                .status(InternStatus.COMPLETED)
                .build();
        completedIntern.setId(2L);

        when(internProfileRepository.findByUserId(20L)).thenReturn(Optional.of(completedIntern));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                attendanceService.initiateCheckIn(20L, request));

        assertTrue(ex.getMessage().contains("Chỉ thực tập sinh đang trong giai đoạn thực tập chính thức (INTERNING) mới được phép thực hiện chấm công"));
    }
}
