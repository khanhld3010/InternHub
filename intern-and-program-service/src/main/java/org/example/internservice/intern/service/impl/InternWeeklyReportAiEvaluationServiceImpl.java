package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.example.internservice.intern.service.InternWeeklyReportAiEvaluationService;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.intern.entity.MentorProfile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.example.internservice.security.CustomUserDetails;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternWeeklyReportAiEvaluationServiceImpl implements InternWeeklyReportAiEvaluationService {

    private final InternWeeklyReportRepository reportRepository;
    private final InternWeeklyReportAiEvaluationRepository aiEvaluationRepository;
    private final InternProfileRepository internProfileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MissionItemRepository missionItemRepository;
    private final AiServiceClient aiServiceClient;

    @Value("${app.internal-token:INTERNAL_SECRET_TOKEN_2026}")
    private String internalToken = "INTERNAL_SECRET_TOKEN_2026";

    @Override
    @Transactional(readOnly = true)
    public WeeklyReportAiEvaluationResponse getActiveEvaluation(Long reportId, String currentUser) {
        InternWeeklyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo tuần ID: " + reportId));

        validateMentorAccess(report);

        return aiEvaluationRepository.findByReportIdAndIsActiveTrue(report.getId())
                .map(this::mapToResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    public WeeklyReportAiEvaluationResponse triggerEvaluation(Long reportId, String currentUser) {
        InternWeeklyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo tuần ID: " + reportId));

        validateMentorAccess(report);

        if (report.getStatus() == WeeklyReportStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể phân tích báo cáo tuần đã được nộp.");
        }

        InternProfile profile = internProfileRepository.findByInternCode(report.getInternCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ thực tập sinh: " + report.getInternCode()));

        // 1. Tính toán phạm vi ngày của tuần báo cáo (Date Range)
        LocalDate startDate = profile.getStartDate() != null ? profile.getStartDate() : report.getReportDate().minusWeeks(report.getWeekNumber());
        LocalDate weekStartDate = startDate.plusWeeks(report.getWeekNumber() - 1);
        LocalDate weekEndDate = weekStartDate.plusDays(6);

        // 2. Lấy danh sách nhiệm vụ Kanban được phân công và lọc theo khoảng thời gian tuần báo cáo
        List<MissionItem> assignedTasks = missionItemRepository.findAssignedItemsByInternIdWithDetails(profile.getId());
        List<AiEvaluateWeeklyReportRequest.KanbanTaskSnapshot> taskSnapshots = assignedTasks != null ? assignedTasks.stream()
                .filter(t -> isTaskInReportWeek(t, weekStartDate, weekEndDate))
                .map(t -> AiEvaluateWeeklyReportRequest.KanbanTaskSnapshot.builder()
                        .id(t.getId())
                        .title(t.getTitle())
                        .description(t.getDescription())
                        .status(t.getStatus() != null ? t.getStatus().name() : "TODO")
                        .priority(t.getPriority() != null ? t.getPriority().name() : "MEDIUM")
                        .dueDate(t.getDueDate())
                        .build())
                .toList() : Collections.emptyList();

        // 2. Chuẩn bị request gọi sang ai-service
        AiEvaluateWeeklyReportRequest request = AiEvaluateWeeklyReportRequest.builder()
                .internCode(report.getInternCode())
                .weekNumber(report.getWeekNumber())
                .completedTasksSummary(report.getCompletedTasksSummary())
                .unfinishedTasksSummary(report.getUnfinishedTasksSummary())
                .difficultiesAndChallenges(report.getDifficultiesAndChallenges())
                .learningsAndKnowledge(report.getLearningsAndKnowledge())
                .kanbanTasks(taskSnapshots)
                .build();

        log.info("[AI-04] Gửi yêu cầu phân tích báo cáo tuần reportId={}, internCode={} sang ai-service", reportId, report.getInternCode());
        AiWeeklyReportEvaluationResult aiResult;
        try {
            aiResult = aiServiceClient.evaluateWeeklyReport(internalToken, request);
        } catch (Exception e) {
            log.error("[AI-04] Lỗi khi gọi ai-service evaluateWeeklyReport: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không thể kết nối đến máy chủ AI, vui lòng thử lại sau: " + e.getMessage());
        }

        if (aiResult == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Máy chủ AI không trả về dữ liệu phân tích.");
        }

        // 3. Quản lý phiên bản: Deactivate các bản ghi cũ và tính version mới
        aiEvaluationRepository.deactivateAllVersionsByReportId(report.getId());
        int currentMaxVersion = aiEvaluationRepository.findMaxVersionByReportId(report.getId());
        int nextVersion = currentMaxVersion + 1;

        InternWeeklyReportAiEvaluation.KanbanAlignmentStatus alignmentStatus;
        try {
            alignmentStatus = InternWeeklyReportAiEvaluation.KanbanAlignmentStatus.valueOf(aiResult.getKanbanAlignmentStatus());
        } catch (Exception e) {
            alignmentStatus = InternWeeklyReportAiEvaluation.KanbanAlignmentStatus.MATCHED;
        }

        InternWeeklyReportAiEvaluation entity = InternWeeklyReportAiEvaluation.builder()
                .reportId(report.getId())
                .internCode(report.getInternCode())
                .weekNumber(report.getWeekNumber())
                .version(nextVersion)
                .isActive(true)
                .progressSummary(aiResult.getProgressSummary())
                .blockersAndRisks(aiResult.getBlockersAndRisks())
                .mentorActionAdvice(aiResult.getMentorActionAdvice())
                .kanbanAlignmentStatus(alignmentStatus)
                .kanbanAlignmentScore(aiResult.getKanbanAlignmentScore() != null ? aiResult.getKanbanAlignmentScore() : 100)
                .kanbanDiscrepancyDetails(aiResult.getKanbanDiscrepancyDetails())
                .suggestedTechnicalScore(aiResult.getSuggestedTechnicalScore() != null ? aiResult.getSuggestedTechnicalScore() : 4)
                .suggestedAttitudeScore(aiResult.getSuggestedAttitudeScore() != null ? aiResult.getSuggestedAttitudeScore() : 5)
                .suggestedTeamworkScore(aiResult.getSuggestedTeamworkScore() != null ? aiResult.getSuggestedTeamworkScore() : 4)
                .suggestedProductivityScore(aiResult.getSuggestedProductivityScore() != null ? aiResult.getSuggestedProductivityScore() : 4)
                .suggestedAverageScore(aiResult.getSuggestedAverageScore() != null ? aiResult.getSuggestedAverageScore() : BigDecimal.valueOf(4.3))
                .suggestedFeedback(aiResult.getSuggestedFeedback())
                .suggestedNextWeekGoals(aiResult.getSuggestedNextWeekGoals())
                .modelName(aiResult.getModelName() != null ? aiResult.getModelName() : "gemini-1.5-pro")
                .promptVersion(aiResult.getPromptVersion() != null ? aiResult.getPromptVersion() : "v1.0-weekly-eval-5stars-202610")
                .executionTimeMs(aiResult.getExecutionTimeMs())
                .requestedBy(currentUser != null ? currentUser : "SYSTEM")
                .build();

        InternWeeklyReportAiEvaluation saved = aiEvaluationRepository.save(entity);
        log.info("[AI-04] Đã lưu kết quả phân tích AI thành công: id={}, version={}", saved.getId(), saved.getVersion());

        return mapToResponse(saved);
    }

    private WeeklyReportAiEvaluationResponse mapToResponse(InternWeeklyReportAiEvaluation entity) {
        return WeeklyReportAiEvaluationResponse.builder()
                .id(entity.getId())
                .reportId(entity.getReportId())
                .internCode(entity.getInternCode())
                .weekNumber(entity.getWeekNumber())
                .version(entity.getVersion())
                .isActive(entity.getIsActive())
                .progressSummary(entity.getProgressSummary())
                .blockersAndRisks(entity.getBlockersAndRisks())
                .mentorActionAdvice(entity.getMentorActionAdvice())
                .kanbanAlignment(WeeklyReportAiEvaluationResponse.KanbanAlignmentInfo.builder()
                        .status(entity.getKanbanAlignmentStatus())
                        .score(entity.getKanbanAlignmentScore())
                        .details(entity.getKanbanDiscrepancyDetails())
                        .build())
                .suggestedAssessment(WeeklyReportAiEvaluationResponse.SuggestedAssessmentInfo.builder()
                        .technicalScore(entity.getSuggestedTechnicalScore())
                        .attitudeScore(entity.getSuggestedAttitudeScore())
                        .teamworkScore(entity.getSuggestedTeamworkScore())
                        .productivityScore(entity.getSuggestedProductivityScore())
                        .averageScore(entity.getSuggestedAverageScore())
                        .feedback(entity.getSuggestedFeedback())
                        .nextWeekGoals(entity.getSuggestedNextWeekGoals())
                        .build())
                .metadata(WeeklyReportAiEvaluationResponse.TechnicalMetadata.builder()
                        .modelName(entity.getModelName())
                        .promptVersion(entity.getPromptVersion())
                        .executionTimeMs(entity.getExecutionTimeMs())
                        .requestedBy(entity.getRequestedBy())
                        .createdAt(entity.getCreatedAt())
                        .build())
                .build();
    }

    private boolean isTaskInReportWeek(MissionItem task, LocalDate weekStartDate, LocalDate weekEndDate) {
        if (task.getDueDate() != null) {
            return !task.getDueDate().isBefore(weekStartDate) && !task.getDueDate().isAfter(weekEndDate);
        }
        if (task.getCreatedAt() != null) {
            LocalDate createdDate = task.getCreatedAt().toLocalDate();
            return !createdDate.isBefore(weekStartDate) && !createdDate.isAfter(weekEndDate);
        }
        return true;
    }

    private void validateMentorAccess(InternWeeklyReport report) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }

        boolean isHrOrAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));
        if (isHrOrAdmin) {
            return;
        }

        CustomUserDetails userDetails = (auth.getPrincipal() instanceof CustomUserDetails ud) ? ud : null;
        if (userDetails != null && userDetails.getUserId() != null && report.getMentorId() != null) {
            List<Long> allowedMentorIds = resolveMentorIds(userDetails.getUserId());
            if (!allowedMentorIds.contains(report.getMentorId())) {
                log.warn("[AI-04 IDOR] Mentor userId={} cố gắng truy cập báo cáo reportId={} thuộc mentorId={}",
                        userDetails.getUserId(), report.getId(), report.getMentorId());
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập hoặc đánh giá báo cáo tuần này.");
            }
        }
    }

    private List<Long> resolveMentorIds(Long mentorId) {
        if (mentorId == null) {
            return Collections.emptyList();
        }
        List<Long> mentorIds = new ArrayList<>();
        mentorIds.add(mentorId);
        mentorProfileRepository.findByUserId(mentorId)
                .ifPresent(mp -> {
                    if (!mentorIds.contains(mp.getId())) {
                        mentorIds.add(mp.getId());
                    }
                });
        mentorProfileRepository.findById(mentorId)
                .ifPresent(mp -> {
                    if (mp.getUserId() != null && !mentorIds.contains(mp.getUserId())) {
                        mentorIds.add(mp.getUserId());
                    }
                });
        return mentorIds;
    }
}
