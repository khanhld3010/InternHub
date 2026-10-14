package org.example.internservice.intern.service;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.dto.request.RequestReportRevisionRequest;
import org.example.internservice.intern.dto.request.WeeklyAssessmentRequest;
import org.example.internservice.intern.dto.response.MentorWeeklyReportReviewResponse;
import org.example.internservice.intern.dto.response.ReportRevisionResponse;
import org.example.internservice.intern.dto.response.WeeklyAssessmentResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.InternWeeklyAssessment;
import org.example.internservice.intern.entity.InternWeeklyReport;
import org.example.internservice.intern.entity.InternWeeklyReportTask;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.entity.enums.WeeklyReportStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyAssessmentRepository;
import org.example.internservice.intern.repository.InternWeeklyReportRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.intern.service.impl.WeeklyAssessmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyAssessmentServiceTest {

    @Mock
    private InternWeeklyAssessmentRepository assessmentRepository;

    @Mock
    private InternWeeklyReportRepository reportRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private NotificationEventDispatcher notificationEventDispatcher;

    @InjectMocks
    private WeeklyAssessmentServiceImpl weeklyAssessmentService;

    private InternProfile sampleIntern;
    private InternWeeklyReport sampleReport;
    private InternWeeklyAssessment sampleAssessment;

    @BeforeEach
    void setUp() {
        sampleIntern = InternProfile.builder()
                .internCode("TTS-001")
                .fullName("Nguyen Van A")
                .appliedPosition("Backend Java Developer")
                .mentorId(10L)
                .mentorName("Mentor Tran")
                .userId(100L)
                .startDate(LocalDate.now().minusWeeks(4))
                .endDate(LocalDate.now().plusWeeks(8))
                .status(InternStatus.INTERNING)
                .build();

        sampleReport = InternWeeklyReport.builder()
                .internCode("TTS-001")
                .mentorId(10L)
                .weekNumber(2)
                .reportDate(LocalDate.now())
                .completedTasksSummary("Hoan thanh Entity va CRUD")
                .unfinishedTasksSummary("Chua xong Redis")
                .difficultiesAndChallenges("Vung Redisson")
                .learningsAndKnowledge("Hoc duoc Spring Data")
                .status(WeeklyReportStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now().minusHours(2))
                .tasks(new ArrayList<>())
                .build();

        InternWeeklyReportTask task1 = InternWeeklyReportTask.builder()
                .taskTitle("Thiet ke Entity")
                .taskStatus("COMPLETED")
                .isCompleted(true)
                .build();
        sampleReport.addTask(task1);

        sampleAssessment = InternWeeklyAssessment.builder()
                .internCode("TTS-001")
                .mentorId(10L)
                .mentorName("Mentor Tran")
                .weekNumber(2)
                .technicalScore(5)
                .attitudeScore(5)
                .teamworkScore(4)
                .productivityScore(4)
                .averageScore(BigDecimal.valueOf(4.5))
                .feedback("Tot lam")
                .nextWeekGoals("Giai quyet Redis")
                .status(InternWeeklyAssessment.AssessmentStatus.DRAFT)
                .build();
    }

    @Test
    @DisplayName("UT-BE-01: Mentor lay chi tiet bao cao doi soat cua TTS phu trach thanh cong (Dual-Pane)")
    void getWeeklyReportForMentor_Success() {
        when(internProfileRepository.findByInternCode("TTS-001")).thenReturn(Optional.of(sampleIntern));
        when(reportRepository.findByInternCodeAndWeekNumberWithTasks("TTS-001", 2)).thenReturn(Optional.of(sampleReport));
        when(assessmentRepository.findByInternCodeAndWeekNumber("TTS-001", 2)).thenReturn(Optional.of(sampleAssessment));

        MentorWeeklyReportReviewResponse response = weeklyAssessmentService.getWeeklyReportForMentor("TTS-001", 2, 10L, "ROLE_MENTOR");

        assertNotNull(response);
        assertEquals("TTS-001", response.getInternCode());
        assertEquals("Nguyen Van A", response.getInternName());
        assertEquals(2, response.getWeekNumber());

        // Kiem tra report
        assertNotNull(response.getReport());
        assertEquals("Hoan thanh Entity va CRUD", response.getReport().getCompletedTasksSummary());
        assertEquals("SUBMITTED", response.getReport().getStatus());
        assertEquals(1, response.getReport().getTasks().size());

        // Kiem tra assessment
        assertNotNull(response.getAssessment());
        assertEquals(BigDecimal.valueOf(4.5), response.getAssessment().getAverageScore());
    }

    @Test
    @DisplayName("UT-BE-02: Nem AccessDeniedException khi Mentor khac truy cap TTS khong phai cua minh (IDOR)")
    void getWeeklyReportForMentor_Fail_IDOR() {
        when(internProfileRepository.findByInternCode("TTS-001")).thenReturn(Optional.of(sampleIntern));

        assertThrows(AccessDeniedException.class, () ->
                weeklyAssessmentService.getWeeklyReportForMentor("TTS-001", 2, 999L, "ROLE_MENTOR")
        );

        verify(reportRepository, never()).findByInternCodeAndWeekNumberWithTasks(any(), any());
    }

    @Test
    @DisplayName("UT-BE-03: Mentor yeu cau sua lai bao cao thanh cong, doi sang REVISION_REQUESTED va ban notif")
    void requestRevision_Success() {
        when(internProfileRepository.findByInternCode("TTS-001")).thenReturn(Optional.of(sampleIntern));
        when(reportRepository.findByInternCodeAndWeekNumber("TTS-001", 2)).thenReturn(Optional.of(sampleReport));
        when(reportRepository.save(any(InternWeeklyReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestReportRevisionRequest req = RequestReportRevisionRequest.builder()
                .revisionNote("Thieu link PR cho task so 1")
                .build();

        ReportRevisionResponse response = weeklyAssessmentService.requestReportRevision("TTS-001", 2, req, 10L, "Mentor Tran", "ROLE_MENTOR");

        assertNotNull(response);
        assertEquals(WeeklyReportStatus.REVISION_REQUESTED.name(), response.getStatus());
        assertEquals("Thieu link PR cho task so 1", response.getRevisionNote());

        assertEquals(WeeklyReportStatus.REVISION_REQUESTED, sampleReport.getStatus());
        assertEquals("Thieu link PR cho task so 1", sampleReport.getRevisionNote());

        verify(notificationEventDispatcher, times(1)).dispatch(any(CreateNotificationInternalRequest.class));
    }

    @Test
    @DisplayName("UT-BE-04: Nem BadRequestException khi yeu cau sua bao cao chua duoc nop (status != SUBMITTED)")
    void requestRevision_Fail_NotSubmitted() {
        sampleReport.setStatus(WeeklyReportStatus.DRAFT);
        when(internProfileRepository.findByInternCode("TTS-001")).thenReturn(Optional.of(sampleIntern));
        when(reportRepository.findByInternCodeAndWeekNumber("TTS-001", 2)).thenReturn(Optional.of(sampleReport));

        RequestReportRevisionRequest req = RequestReportRevisionRequest.builder()
                .revisionNote("Sua lai di")
                .build();

        assertThrows(BadRequestException.class, () ->
                weeklyAssessmentService.requestReportRevision("TTS-001", 2, req, 10L, "Mentor Tran", "ROLE_MENTOR")
        );

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-BE-05: Khi isPublish = true, assessment co status PUBLISHED va report doi sang REVIEWED")
    void saveAssessment_Publish_SyncReportStatus() {
        when(internProfileRepository.findByInternCode("TTS-001")).thenReturn(Optional.of(sampleIntern));
        when(assessmentRepository.findByInternCodeAndWeekNumber("TTS-001", 2)).thenReturn(Optional.of(sampleAssessment));
        when(assessmentRepository.save(any(InternWeeklyAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findByInternCodeAndWeekNumber("TTS-001", 2)).thenReturn(Optional.of(sampleReport));
        when(reportRepository.save(any(InternWeeklyReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WeeklyAssessmentRequest req = WeeklyAssessmentRequest.builder()
                .weekNumber(2)
                .technicalScore(5)
                .attitudeScore(5)
                .teamworkScore(5)
                .productivityScore(5)
                .feedback("Xuat sac")
                .nextWeekGoals("Phat huy")
                .isPublish(true)
                .build();

        WeeklyAssessmentResponse response = weeklyAssessmentService.saveAssessment("TTS-001", req, 10L, "Mentor Tran", "ROLE_MENTOR");

        assertNotNull(response);
        assertEquals("PUBLISHED", response.getStatus());
        assertEquals(BigDecimal.valueOf(5.0), response.getAverageScore());

        // Kiem tra report da duoc dong bo sang REVIEWED
        assertEquals(WeeklyReportStatus.REVIEWED, sampleReport.getStatus());
        verify(reportRepository, times(1)).save(sampleReport);
        verify(notificationEventDispatcher, times(1)).dispatch(any(CreateNotificationInternalRequest.class));
    }

    @Test
    @DisplayName("UT-BE-06: Mentor cham diem thanh cong cho tuan ma TTS chua nop bao cao")
    void saveAssessment_Success_WithoutReport() {
        when(internProfileRepository.findByInternCode("TTS-001")).thenReturn(Optional.of(sampleIntern));
        when(assessmentRepository.findByInternCodeAndWeekNumber("TTS-001", 3)).thenReturn(Optional.empty());
        when(assessmentRepository.save(any(InternWeeklyAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findByInternCodeAndWeekNumber("TTS-001", 3)).thenReturn(Optional.empty());

        WeeklyAssessmentRequest req = WeeklyAssessmentRequest.builder()
                .weekNumber(3)
                .technicalScore(3)
                .attitudeScore(3)
                .teamworkScore(3)
                .productivityScore(3)
                .feedback("Chua nop bao cao nhung van co lam task")
                .isPublish(true)
                .build();

        WeeklyAssessmentResponse response = weeklyAssessmentService.saveAssessment("TTS-001", req, 10L, "Mentor Tran", "ROLE_MENTOR");

        assertNotNull(response);
        assertEquals("PUBLISHED", response.getStatus());
        assertEquals(BigDecimal.valueOf(3.0), response.getAverageScore());
        verify(notificationEventDispatcher, times(1)).dispatch(any());
    }

    @Test
    @DisplayName("UT-BE-07: Mentor cap nhat lai danh gia da publish thanh cong, tinh lai diem TB chuan")
    void saveAssessment_UpdatePublishedAssessment() {
        sampleAssessment.setStatus(InternWeeklyAssessment.AssessmentStatus.PUBLISHED);
        sampleAssessment.setPublishedAt(LocalDateTime.now().minusDays(1));

        when(internProfileRepository.findByInternCode("TTS-001")).thenReturn(Optional.of(sampleIntern));
        when(assessmentRepository.findByInternCodeAndWeekNumber("TTS-001", 2)).thenReturn(Optional.of(sampleAssessment));
        when(assessmentRepository.save(any(InternWeeklyAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findByInternCodeAndWeekNumber("TTS-001", 2)).thenReturn(Optional.of(sampleReport));

        WeeklyAssessmentRequest req = WeeklyAssessmentRequest.builder()
                .weekNumber(2)
                .technicalScore(4)
                .attitudeScore(4)
                .teamworkScore(4)
                .productivityScore(4)
                .feedback("Cap nhat lai diem")
                .isPublish(true)
                .build();

        WeeklyAssessmentResponse response = weeklyAssessmentService.saveAssessment("TTS-001", req, 10L, "Mentor Tran", "ROLE_MENTOR");

        assertNotNull(response);
        assertEquals("PUBLISHED", response.getStatus());
        assertEquals(BigDecimal.valueOf(4.0), response.getAverageScore());
        assertEquals("Cap nhat lai diem", response.getFeedback());
    }
}
