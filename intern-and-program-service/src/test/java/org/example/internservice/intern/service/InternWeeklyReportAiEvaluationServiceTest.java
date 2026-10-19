package org.example.internservice.intern.service;

import org.example.internservice.intern.client.AiServiceClient;
import org.example.internservice.intern.client.dto.AiEvaluateWeeklyReportRequest;
import org.example.internservice.intern.client.dto.AiWeeklyReportEvaluationResult;
import org.example.internservice.intern.dto.response.WeeklyReportAiEvaluationResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.InternWeeklyReport;
import org.example.internservice.intern.entity.InternWeeklyReportAiEvaluation;
import org.example.internservice.intern.entity.enums.WeeklyReportStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyReportAiEvaluationRepository;
import org.example.internservice.intern.repository.InternWeeklyReportRepository;
import org.example.internservice.intern.service.impl.InternWeeklyReportAiEvaluationServiceImpl;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternWeeklyReportAiEvaluationServiceTest {

    @Mock
    private InternWeeklyReportRepository reportRepository;

    @Mock
    private InternWeeklyReportAiEvaluationRepository aiEvaluationRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private org.example.internservice.intern.repository.MentorProfileRepository mentorProfileRepository;

    @Mock
    private MissionItemRepository missionItemRepository;

    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private InternWeeklyReportAiEvaluationServiceImpl aiEvaluationService;

    private InternWeeklyReport mockReport;
    private InternProfile mockProfile;
    private InternWeeklyReportAiEvaluation mockAiEval;
    private AiWeeklyReportEvaluationResult mockAiResult;

    @BeforeEach
    void setUp() {
        mockReport = InternWeeklyReport.builder()
                .internCode("INT-2026-001")
                .mentorId(10L)
                .weekNumber(2)
                .status(WeeklyReportStatus.SUBMITTED)
                .completedTasksSummary("Hoàn thành module thanh toán và viết unit test.")
                .unfinishedTasksSummary("Chưa xong webhook.")
                .difficultiesAndChallenges("Gặp khó khăn tài liệu bên thứ ba.")
                .learningsAndKnowledge("Nắm vững chữ ký số HMAC.")
                .reportDate(LocalDate.now())
                .build();
        mockReport.setId(100L);

        mockProfile = InternProfile.builder()
                .internCode("INT-2026-001")
                .fullName("Nguyễn Văn A")
                .mentorId(10L)
                .build();
        mockProfile.setId(50L);

        mockAiEval = InternWeeklyReportAiEvaluation.builder()
                .reportId(100L)
                .internCode("INT-2026-001")
                .weekNumber(2)
                .version(1)
                .isActive(true)
                .progressSummary("Hoàn thành thanh toán.")
                .blockersAndRisks("Webhook lỗi.")
                .mentorActionAdvice("Hỗ trợ đối soát checksum.")
                .kanbanAlignmentStatus(InternWeeklyReportAiEvaluation.KanbanAlignmentStatus.MATCHED)
                .kanbanAlignmentScore(90)
                .kanbanDiscrepancyDetails("Khớp task.")
                .suggestedTechnicalScore(4)
                .suggestedAttitudeScore(5)
                .suggestedTeamworkScore(4)
                .suggestedProductivityScore(4)
                .suggestedAverageScore(BigDecimal.valueOf(4.3))
                .suggestedFeedback("Làm tốt.")
                .suggestedNextWeekGoals("Fix webhook.")
                .modelName("gemini-1.5-pro")
                .promptVersion("v1.0-weekly-eval-5stars-202610")
                .requestedBy("mentor_10")
                .build();
        mockAiEval.setId(1L);

        mockAiResult = AiWeeklyReportEvaluationResult.builder()
                .progressSummary("Hoàn thành thanh toán.")
                .blockersAndRisks("Webhook lỗi.")
                .mentorActionAdvice("Hỗ trợ đối soát checksum.")
                .kanbanAlignmentStatus("MATCHED")
                .kanbanAlignmentScore(90)
                .kanbanDiscrepancyDetails("Khớp task.")
                .suggestedTechnicalScore(4)
                .suggestedAttitudeScore(5)
                .suggestedTeamworkScore(4)
                .suggestedProductivityScore(4)
                .suggestedAverageScore(BigDecimal.valueOf(4.3))
                .suggestedFeedback("Làm tốt.")
                .suggestedNextWeekGoals("Fix webhook.")
                .modelName("gemini-1.5-pro")
                .promptVersion("v1.0-weekly-eval-5stars-202610")
                .executionTimeMs(1500L)
                .build();
    }

    @Test
    @DisplayName("getActiveEvaluation: Trả về kết quả active thành công")
    void getActiveEvaluation_Success() {
        when(reportRepository.findById(100L)).thenReturn(Optional.of(mockReport));
        when(aiEvaluationRepository.findByReportIdAndIsActiveTrue(100L)).thenReturn(Optional.of(mockAiEval));

        WeeklyReportAiEvaluationResponse response = aiEvaluationService.getActiveEvaluation(100L, "mentor_10");

        assertThat(response).isNotNull();
        assertThat(response.getReportId()).isEqualTo(100L);
        assertThat(response.getSuggestedAssessment().getTechnicalScore()).isEqualTo(4);
        assertThat(response.getSuggestedAssessment().getAverageScore()).isEqualTo(BigDecimal.valueOf(4.3));
        assertThat(response.getKanbanAlignment().getStatus()).isEqualTo(InternWeeklyReportAiEvaluation.KanbanAlignmentStatus.MATCHED);
    }

    @Test
    @DisplayName("triggerEvaluation: Phân tích báo cáo tuần lần đầu (Version 1) thành công")
    void triggerEvaluation_FirstTime_Success() {
        when(reportRepository.findById(100L)).thenReturn(Optional.of(mockReport));
        when(internProfileRepository.findByInternCode("INT-2026-001")).thenReturn(Optional.of(mockProfile));

        MissionItem task1 = MissionItem.builder()
                .title("Tích hợp cổng thanh toán")
                .description("VNPAY sandbox")
                .status(MissionItemStatus.COMPLETED)
                .priority(MissionPriority.HIGH)
                .dueDate(LocalDate.now())
                .build();
        task1.setId(101L);

        when(missionItemRepository.findAssignedItemsByInternIdWithDetails(50L)).thenReturn(List.of(task1));
        when(aiEvaluationRepository.findMaxVersionByReportId(100L)).thenReturn(0);
        when(aiServiceClient.evaluateWeeklyReport(anyString(), any(AiEvaluateWeeklyReportRequest.class))).thenReturn(mockAiResult);
        when(aiEvaluationRepository.save(any(InternWeeklyReportAiEvaluation.class))).thenAnswer(invocation -> {
            InternWeeklyReportAiEvaluation entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        WeeklyReportAiEvaluationResponse response = aiEvaluationService.triggerEvaluation(100L, "mentor_10");

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getSuggestedAssessment().getAttitudeScore()).isEqualTo(5);
        verify(aiEvaluationRepository).deactivateAllVersionsByReportId(100L);
        verify(aiEvaluationRepository).save(any(InternWeeklyReportAiEvaluation.class));
    }

    @Test
    @DisplayName("triggerEvaluation: Tái phân tích (Regenerate) tăng Version lên 2 và deactivate version cũ")
    void triggerEvaluation_Regenerate_IncreasesVersion() {
        when(reportRepository.findById(100L)).thenReturn(Optional.of(mockReport));
        when(internProfileRepository.findByInternCode("INT-2026-001")).thenReturn(Optional.of(mockProfile));
        when(missionItemRepository.findAssignedItemsByInternIdWithDetails(50L)).thenReturn(Collections.emptyList());
        when(aiEvaluationRepository.findMaxVersionByReportId(100L)).thenReturn(1);
        when(aiServiceClient.evaluateWeeklyReport(anyString(), any(AiEvaluateWeeklyReportRequest.class))).thenReturn(mockAiResult);
        when(aiEvaluationRepository.save(any(InternWeeklyReportAiEvaluation.class))).thenAnswer(invocation -> {
            InternWeeklyReportAiEvaluation entity = invocation.getArgument(0);
            entity.setId(2L);
            return entity;
        });

        WeeklyReportAiEvaluationResponse response = aiEvaluationService.triggerEvaluation(100L, "mentor_10");

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(2);
        assertThat(response.getIsActive()).isTrue();
        verify(aiEvaluationRepository).deactivateAllVersionsByReportId(100L);
    }

    @Test
    @DisplayName("triggerEvaluation: Từ chối nếu báo cáo đang ở trạng thái DRAFT")
    void triggerEvaluation_ThrowsWhenDraft() {
        mockReport.setStatus(WeeklyReportStatus.DRAFT);
        when(reportRepository.findById(100L)).thenReturn(Optional.of(mockReport));

        assertThatThrownBy(() -> aiEvaluationService.triggerEvaluation(100L, "mentor_10"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Chỉ có thể phân tích báo cáo tuần đã được nộp");
    }

    @Test
    @DisplayName("validateMentorAccess: Chặn IDOR nếu Mentor không phụ trách báo cáo")
    void triggerEvaluation_ThrowsForbidden_WhenMentorNotAssigned() {
        org.example.internservice.security.CustomUserDetails otherMentor = org.example.internservice.security.CustomUserDetails.builder()
                .userId(999L)
                .username("other_mentor")
                .authorities(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MENTOR")))
                .build();
        org.springframework.security.core.Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(otherMentor, null, otherMentor.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(mockReport)); // mockReport has mentorId = 10L
        when(mentorProfileRepository.findByUserId(999L)).thenReturn(Optional.empty());
        when(mentorProfileRepository.findById(999L)).thenReturn(Optional.empty());

        try {
            assertThatThrownBy(() -> aiEvaluationService.triggerEvaluation(100L, "other_mentor"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Bạn không có quyền truy cập hoặc đánh giá báo cáo tuần này");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("triggerEvaluation: Lọc đúng các Kanban Task thuộc khoảng thời gian tuần báo cáo")
    void triggerEvaluation_FiltersTasksByDateRange() {
        when(reportRepository.findById(100L)).thenReturn(Optional.of(mockReport));
        mockProfile.setStartDate(LocalDate.now().minusWeeks(2));
        when(internProfileRepository.findByInternCode("INT-2026-001")).thenReturn(Optional.of(mockProfile));

        // Task 1: Trong tuần báo cáo (Tuần 2)
        MissionItem taskInWeek = MissionItem.builder()
                .title("Task đúng tuần")
                .dueDate(LocalDate.now().minusWeeks(1).plusDays(2))
                .status(MissionItemStatus.COMPLETED)
                .build();
        taskInWeek.setId(201L);

        // Task 2: Hạn chót trước 4 tuần (Task quá khứ)
        MissionItem taskOutOfWeek = MissionItem.builder()
                .title("Task cũ 4 tuần trước")
                .dueDate(LocalDate.now().minusWeeks(4))
                .status(MissionItemStatus.COMPLETED)
                .build();
        taskOutOfWeek.setId(202L);

        when(missionItemRepository.findAssignedItemsByInternIdWithDetails(50L)).thenReturn(List.of(taskInWeek, taskOutOfWeek));
        when(aiEvaluationRepository.findMaxVersionByReportId(100L)).thenReturn(0);
        when(aiServiceClient.evaluateWeeklyReport(anyString(), any(AiEvaluateWeeklyReportRequest.class))).thenReturn(mockAiResult);
        when(aiEvaluationRepository.save(any(InternWeeklyReportAiEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WeeklyReportAiEvaluationResponse response = aiEvaluationService.triggerEvaluation(100L, "mentor_10");

        assertThat(response).isNotNull();
        verify(aiServiceClient).evaluateWeeklyReport(anyString(), org.mockito.ArgumentMatchers.argThat(req ->
                req.getKanbanTasks().size() == 1 && req.getKanbanTasks().get(0).getId().equals(201L)
        ));
    }
}
