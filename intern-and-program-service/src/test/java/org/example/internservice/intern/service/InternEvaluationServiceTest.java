package org.example.internservice.intern.service;

import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.example.internservice.contract.entity.Contract;
import org.example.internservice.contract.entity.ContractRevision;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;
import org.example.internservice.contract.repository.ContractRevisionRepository;
import org.example.internservice.contract.repository.DynamicContractRepository;
import org.example.internservice.intern.client.IdentityServiceClient;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.dto.HrApproveEvaluationRequest;
import org.example.internservice.intern.dto.InternEvaluationResponse;
import org.example.internservice.intern.entity.InternEvaluation;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternEvaluationRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyAssessmentRepository;
import org.example.internservice.intern.service.impl.InternEvaluationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternEvaluationServiceTest {

    @Mock
    private InternEvaluationRepository evaluationRepository;

    @Mock
    private InternWeeklyAssessmentRepository weeklyAssessmentRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private NotificationEventDispatcher notificationEventDispatcher;

    @Mock
    private IdentityServiceClient identityServiceClient;

    @Mock
    private DynamicContractRepository dynamicContractRepository;

    @Mock
    private ContractRevisionRepository contractRevisionRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @InjectMocks
    private InternEvaluationServiceImpl evaluationService;

    private InternProfile sampleProfile;
    private InternEvaluation sampleEvaluation;

    @BeforeEach
    void setUp() {
        sampleProfile = new InternProfile();
        sampleProfile.setId(100L);
        sampleProfile.setUserId(555L);
        sampleProfile.setInternCode("INT2026001");
        sampleProfile.setFullName("Nguyen Van A");
        sampleProfile.setStatus(InternStatus.INTERNING);

        sampleEvaluation = InternEvaluation.builder()
                .id(1L)
                .internCode("INT2026001")
                .mentorId(10L)
                .mentorName("Mentor Tran")
                .evaluationType("FINAL")
                .status("SUBMITTED")
                .technicalScore(BigDecimal.valueOf(8.5))
                .attitudeScore(BigDecimal.valueOf(9.0))
                .softSkillsScore(BigDecimal.valueOf(8.0))
                .finalScore(BigDecimal.valueOf(8.6))
                .build();
    }

    @Test
    @DisplayName("Lỗi 28 [P0]: Khi HR phê duyệt đánh giá tốt nghiệp, hệ thống phải tự động gọi IdentityServiceClient để khóa tài khoản người dùng")
    void hrApproveEvaluation_shouldCallIdentityServiceClientToLockAccount() {
        HrApproveEvaluationRequest request = new HrApproveEvaluationRequest();
        request.setHrComments("Đạt loại xuất sắc");
        request.setInternshipResult("PASSED");

        Contract contract = new Contract();
        contract.setId(200L);
        contract.setContractNumber("HD-2026-001");
        contract.setStatus(DynamicContractStatus.ACTIVE);
        contract.setInternProfile(sampleProfile);
        contract.setCurrentRevisionId(300L);

        ContractRevision revision = new ContractRevision();
        revision.setId(300L);
        revision.setStatus(DynamicContractStatus.ACTIVE);

        when(evaluationRepository.findByInternCodeAndEvaluationType("INT2026001", "FINAL"))
                .thenReturn(Optional.of(sampleEvaluation));
        when(evaluationRepository.save(any(InternEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(internProfileRepository.findByInternCode("INT2026001")).thenReturn(Optional.of(sampleProfile));
        when(dynamicContractRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(contract));
        when(contractRevisionRepository.findById(300L)).thenReturn(Optional.of(revision));
        when(identityServiceClient.toggleUserAccountStatus(555L)).thenReturn(true);
        when(attendanceRepository.findByInternId(100L)).thenReturn(Collections.emptyList());

        InternEvaluationResponse response = evaluationService.hrApproveEvaluation("INT2026001", request, "hr_manager");

        assertNotNull(response);
        assertEquals("APPROVED", sampleEvaluation.getStatus());
        assertEquals("hr_manager", sampleEvaluation.getApprovedBy());
        assertEquals(InternStatus.COMPLETED, sampleProfile.getStatus());
        assertEquals(DynamicContractStatus.EXPIRED, contract.getStatus());
        assertEquals(DynamicContractStatus.EXPIRED, revision.getStatus());

        // Verify Lỗi 28: toggleUserAccountStatus được gọi cho userId 555L
        verify(identityServiceClient, times(1)).toggleUserAccountStatus(555L);
        verify(dynamicContractRepository, times(1)).save(contract);
    }

    @Test
    @DisplayName("Lỗi 27 [P1]: Khi trả về response đánh giá, hệ thống phải tổng hợp đầy đủ số ca đi muộn, về sớm, vắng mặt và tổng giờ công")
    void getEvaluationByType_shouldAggregateAttendanceDisciplinaryRecords() {
        when(evaluationRepository.findByInternCodeAndEvaluationType("INT2026001", "FINAL"))
                .thenReturn(Optional.of(sampleEvaluation));
        when(internProfileRepository.findByInternCode("INT2026001")).thenReturn(Optional.of(sampleProfile));

        List<Attendance> attendances = new ArrayList<>();

        // 1 ca đúng giờ, 8h
        attendances.add(Attendance.builder()
                .internId(100L)
                .workDate(LocalDate.of(2026, 10, 1))
                .status(AttendanceStatus.ON_TIME)
                .totalWorkingHours(8.0)
                .build());

        // 1 ca đi muộn, 7.5h
        attendances.add(Attendance.builder()
                .internId(100L)
                .workDate(LocalDate.of(2026, 10, 2))
                .status(AttendanceStatus.LATE)
                .totalWorkingHours(7.5)
                .build());

        // 1 ca về sớm, 6.0h
        attendances.add(Attendance.builder()
                .internId(100L)
                .workDate(LocalDate.of(2026, 10, 3))
                .status(AttendanceStatus.EARLY_LEAVE)
                .totalWorkingHours(6.0)
                .build());

        // 1 ca vừa muộn vừa về sớm, 5.0h
        attendances.add(Attendance.builder()
                .internId(100L)
                .workDate(LocalDate.of(2026, 10, 4))
                .status(AttendanceStatus.LATE_AND_EARLY_LEAVE)
                .totalWorkingHours(5.0)
                .build());

        // 1 ca vắng mặt (0h)
        attendances.add(Attendance.builder()
                .internId(100L)
                .workDate(LocalDate.of(2026, 10, 5))
                .status(AttendanceStatus.ABSENT)
                .totalWorkingHours(0.0)
                .build());

        when(attendanceRepository.findByInternId(100L)).thenReturn(attendances);

        InternEvaluationResponse response = evaluationService.getEvaluationByType("INT2026001", "FINAL");

        assertNotNull(response);
        // lateCount: 1 (LATE) + 1 (LATE_AND_EARLY_LEAVE) = 2
        assertEquals(2, response.getLateCount());
        // earlyLeaveCount: 1 (EARLY_LEAVE) + 1 (LATE_AND_EARLY_LEAVE) = 2
        assertEquals(2, response.getEarlyLeaveCount());
        // absentCount: 1
        assertEquals(1, response.getAbsentCount());
        // totalWorkingHours: 8.0 + 7.5 + 6.0 + 5.0 + 0 = 26.5h
        assertEquals(26.5, response.getTotalWorkingHours());
    }

    @Test
    @DisplayName("Lỗi 35 [P1] (EVAL-02): Đánh giá chưa APPROVED phải ẩn các trường đề xuất nhạy cảm recommendation, hrComments đối với người dùng không phải cán bộ")
    void getEvaluationByType_whenUnapproved_shouldMaskSensitiveRecommendationNotes() {
        sampleEvaluation.setStatus("SUBMITTED");
        sampleEvaluation.setRecommendation("FAIL");
        sampleEvaluation.setRecommendationNote("Thái độ làm việc không phù hợp với văn hóa nội bộ công ty");
        sampleEvaluation.setHrComments("Ý kiến nội bộ HR xem xét không tuyển dụng");

        when(evaluationRepository.findByInternCodeAndEvaluationType("INT2026001", "FINAL"))
                .thenReturn(Optional.of(sampleEvaluation));
        when(internProfileRepository.findByInternCode("INT2026001")).thenReturn(Optional.of(sampleProfile));
        when(attendanceRepository.findByInternId(100L)).thenReturn(Collections.emptyList());

        InternEvaluationResponse response = evaluationService.getEvaluationByType("INT2026001", "FINAL");

        assertNotNull(response);
        assertEquals("SUBMITTED", response.getStatus());
        // Bị ẩn (null) để tránh rò rỉ thông tin nhạy cảm cho sinh viên trước khi HR duyệt
        assertNull(response.getRecommendation());
        assertNull(response.getRecommendationNote());
        assertNull(response.getHrComments());
    }
}
