package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.dto.request.SaveWeeklyReportRequest;
import org.example.internservice.intern.dto.response.SuggestedKanbanTasksResponse;
import org.example.internservice.intern.dto.response.WeeklyReportDetailResponse;
import org.example.internservice.intern.dto.response.WeeklyReportTimelineResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.InternWeeklyAssessment;
import org.example.internservice.intern.entity.InternWeeklyReport;
import org.example.internservice.intern.entity.InternWeeklyReportTask;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.entity.enums.WeeklyReportStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyAssessmentRepository;
import org.example.internservice.intern.repository.InternWeeklyReportRepository;
import org.example.internservice.intern.service.InternWeeklyReportService;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InternWeeklyReportServiceImpl implements InternWeeklyReportService {

    private final InternWeeklyReportRepository reportRepository;
    private final InternProfileRepository internProfileRepository;
    private final InternWeeklyAssessmentRepository assessmentRepository;
    private final MissionItemRepository missionItemRepository;
    private final NotificationEventDispatcher notificationEventDispatcher;

    @Override
    public WeeklyReportTimelineResponse getTimeline(Long userId) {
        log.info("Lay danh sach timeline bao cao tuan cho user ID: {}", userId);
        InternProfile intern = getActiveInternProfile(userId);

        int currentWeek = calculateCurrentWeek(intern.getStartDate());
        int totalWeeks = calculateTotalWeeks(intern.getStartDate(), intern.getEndDate());

        List<InternWeeklyReport> existingReports = reportRepository.findByInternCodeOrderByWeekNumberAsc(intern.getInternCode());
        Map<Integer, InternWeeklyReport> reportMap = existingReports.stream()
                .collect(Collectors.toMap(InternWeeklyReport::getWeekNumber, r -> r, (r1, r2) -> r1));

        List<InternWeeklyAssessment> assessments = assessmentRepository.findByInternCodeOrderByWeekNumberDesc(intern.getInternCode());
        Map<Integer, InternWeeklyAssessment> assessmentMap = assessments.stream()
                .filter(a -> a.getStatus() == InternWeeklyAssessment.AssessmentStatus.PUBLISHED)
                .collect(Collectors.toMap(InternWeeklyAssessment::getWeekNumber, a -> a, (a1, a2) -> a1));

        List<WeeklyReportTimelineResponse.WeeklyReportItem> timelineItems = new ArrayList<>();
        LocalDate startDate = intern.getStartDate() != null ? intern.getStartDate() : LocalDate.now();

        for (int w = 1; w <= totalWeeks; w++) {
            LocalDate weekStart = startDate.plusDays((long) (w - 1) * 7);
            LocalDate weekEnd = weekStart.plusDays(6);

            InternWeeklyReport report = reportMap.get(w);
            InternWeeklyAssessment assessment = assessmentMap.get(w);

            String status = "NOT_STARTED";
            String statusDisplayName = "Chưa tạo";
            LocalDateTime submittedAt = null;

            if (report != null) {
                submittedAt = report.getSubmittedAt();
                if (assessment != null) {
                    status = "REVIEWED";
                    statusDisplayName = "Đã được đánh giá";
                } else if (report.getStatus() == WeeklyReportStatus.REVISION_REQUESTED) {
                    status = "REVISION_REQUESTED";
                    statusDisplayName = "Yêu cầu chỉnh sửa lại";
                } else if (report.getStatus() == WeeklyReportStatus.SUBMITTED) {
                    status = "SUBMITTED";
                    statusDisplayName = "Đã nộp, chờ đánh giá";
                } else {
                    status = "DRAFT";
                    statusDisplayName = "Bản nháp";
                }
            }

            timelineItems.add(WeeklyReportTimelineResponse.WeeklyReportItem.builder()
                    .weekNumber(w)
                    .startDate(weekStart)
                    .endDate(weekEnd)
                    .status(status)
                    .statusDisplayName(statusDisplayName)
                    .submittedAt(submittedAt)
                    .mentorAverageScore(assessment != null ? assessment.getAverageScore() : null)
                    .mentorFeedback(assessment != null ? assessment.getFeedback() : null)
                    .build());
        }

        return WeeklyReportTimelineResponse.builder()
                .currentWeek(currentWeek)
                .totalWeeks(totalWeeks)
                .reports(timelineItems)
                .build();
    }

    @Override
    public SuggestedKanbanTasksResponse getSuggestedKanbanTasks(Long userId, Integer weekNumber) {
        log.info("Lay goi y nhiem vu Kanban tuan {} cho user ID: {}", weekNumber, userId);
        InternProfile intern = getActiveInternProfile(userId);

        List<MissionItem> assignedItems = missionItemRepository.findAssignedItemsByInternIdWithDetails(intern.getId());

        List<SuggestedKanbanTasksResponse.SuggestedTaskItem> completedTasks = new ArrayList<>();
        List<SuggestedKanbanTasksResponse.SuggestedTaskItem> unfinishedTasks = new ArrayList<>();

        for (MissionItem item : assignedItems) {
            boolean isOverdue = item.getDueDate() != null && item.getDueDate().isBefore(LocalDate.now())
                    && item.getStatus() != MissionItemStatus.COMPLETED;

            SuggestedKanbanTasksResponse.SuggestedTaskItem taskItem = SuggestedKanbanTasksResponse.SuggestedTaskItem.builder()
                    .missionItemId(item.getId())
                    .title(item.getTitle())
                    .status(item.getStatus().name())
                    .statusDisplayName(getStatusDisplayName(item.getStatus()))
                    .dueDate(item.getDueDate())
                    .isOverdue(isOverdue)
                    .submissionUrl(item.getSubmissionUrl())
                    .completionNote(item.getCompletionNote())
                    .submittedAt(item.getSubmittedAt())
                    .build();

            if (item.getStatus() == MissionItemStatus.COMPLETED) {
                completedTasks.add(taskItem);
            } else {
                unfinishedTasks.add(taskItem);
            }
        }

        return SuggestedKanbanTasksResponse.builder()
                .weekNumber(weekNumber)
                .completedTasks(completedTasks)
                .unfinishedTasks(unfinishedTasks)
                .build();
    }

    @Override
    @Transactional
    public WeeklyReportDetailResponse saveOrUpdateReport(Long userId, SaveWeeklyReportRequest request) {
        log.info("Luu bao cao tuan {} cho user ID: {} (isSubmit: {})", request.getWeekNumber(), userId, request.getIsSubmit());
        InternProfile intern = getActiveInternProfile(userId);

        validateWeekNumber(intern.getStartDate(), request.getWeekNumber());

        // Kiểm tra xem Mentor đã công bố đánh giá cho tuần này chưa
        checkIfMentorAlreadyPublished(intern.getInternCode(), request.getWeekNumber());

        boolean isSubmit = Boolean.TRUE.equals(request.getIsSubmit());
        if (isSubmit && (request.getCompletedTasksSummary() == null || request.getCompletedTasksSummary().trim().isEmpty())) {
            throw new BadRequestException("Nội dung tóm tắt công việc hoàn thành không được để trống khi nộp báo cáo");
        }

        WeeklyReportStatus targetStatus = isSubmit ? WeeklyReportStatus.SUBMITTED : WeeklyReportStatus.DRAFT;

        // Upsert: Tìm bản ghi đã có hoặc tạo mới
        InternWeeklyReport report = reportRepository.findByInternCodeAndWeekNumber(intern.getInternCode(), request.getWeekNumber())
                .orElse(InternWeeklyReport.builder()
                        .internCode(intern.getInternCode())
                        .mentorId(intern.getMentorId() != null ? intern.getMentorId() : 0L)
                        .weekNumber(request.getWeekNumber())
                        .build());

        report.setReportDate(request.getReportDate() != null ? request.getReportDate() : LocalDate.now());
        report.setCompletedTasksSummary(request.getCompletedTasksSummary() != null ? request.getCompletedTasksSummary() : "");
        report.setUnfinishedTasksSummary(request.getUnfinishedTasksSummary());
        report.setDifficultiesAndChallenges(request.getDifficultiesAndChallenges());
        report.setLearningsAndKnowledge(request.getLearningsAndKnowledge());
        report.setNextWeekPlan(request.getNextWeekPlan());
        report.setReportAttachmentUrl(request.getReportAttachmentUrl());
        report.setStatus(targetStatus);

        if (isSubmit) {
            report.setSubmittedAt(LocalDateTime.now());
        }

        // Cập nhật snapshot tasks
        report.clearTasks();
        if (request.getTasks() != null) {
            for (SaveWeeklyReportRequest.TaskItem t : request.getTasks()) {
                InternWeeklyReportTask taskEntity = InternWeeklyReportTask.builder()
                        .missionItemId(t.getMissionItemId())
                        .taskTitle(t.getTaskTitle())
                        .taskStatus(t.getTaskStatus())
                        .submissionUrl(t.getSubmissionUrl())
                        .note(t.getNote())
                        .isCompleted(Boolean.TRUE.equals(t.getIsCompleted()))
                        .build();
                report.addTask(taskEntity);
            }
        }

        InternWeeklyReport saved = reportRepository.save(report);

        // Bắn thông báo realtime nếu là hành động nộp chính thức
        if (isSubmit && intern.getMentorId() != null) {
            dispatchSubmitNotification(intern, saved.getWeekNumber(), saved.getId(), userId);
        }

        return mapToDetailResponse(saved, intern);
    }

    @Override
    @Transactional
    public WeeklyReportDetailResponse submitReport(Long userId, Integer weekNumber) {
        log.info("Nop chinh thuc bao cao tuan {} cho user ID: {}", weekNumber, userId);
        InternProfile intern = getActiveInternProfile(userId);

        validateWeekNumber(intern.getStartDate(), weekNumber);
        checkIfMentorAlreadyPublished(intern.getInternCode(), weekNumber);

        InternWeeklyReport report = reportRepository.findByInternCodeAndWeekNumberWithTasks(intern.getInternCode(), weekNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa tìm thấy bản ghi báo cáo tuần " + weekNumber + ". Vui lòng tạo nội dung trước khi nộp"));

        if (report.getCompletedTasksSummary() == null || report.getCompletedTasksSummary().trim().isEmpty()) {
            throw new BadRequestException("Nội dung tóm tắt công việc hoàn thành không được để trống khi nộp báo cáo");
        }

        report.setStatus(WeeklyReportStatus.SUBMITTED);
        report.setSubmittedAt(LocalDateTime.now());
        InternWeeklyReport saved = reportRepository.save(report);

        if (intern.getMentorId() != null) {
            dispatchSubmitNotification(intern, weekNumber, saved.getId(), userId);
        }

        return mapToDetailResponse(saved, intern);
    }

    @Override
    public WeeklyReportDetailResponse getMyReportDetail(Long userId, Integer weekNumber) {
        log.info("Xem chi tiet bao cao tuan {} cho user ID: {}", weekNumber, userId);
        InternProfile intern = getActiveInternProfile(userId);

        InternWeeklyReport report = reportRepository.findByInternCodeAndWeekNumberWithTasks(intern.getInternCode(), weekNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo cho Tuần " + weekNumber));

        return mapToDetailResponse(report, intern);
    }

    // --- Private Helper Methods ---

    private InternProfile getActiveInternProfile(Long userId) {
        InternProfile profile = internProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh cho tài khoản hiện tại"));

        if (profile.getStatus() != InternStatus.INTERNING && profile.getStatus() != InternStatus.APPROVED) {
            throw new BadRequestException("Hồ sơ thực tập sinh đang ở trạng thái [" + profile.getStatus() + "], không thể thực hiện báo cáo tuần");
        }
        return profile;
    }

    private int calculateCurrentWeek(LocalDate startDate) {
        if (startDate == null) return 1;
        long days = ChronoUnit.DAYS.between(startDate, LocalDate.now());
        return days > 0 ? (int) (days / 7) + 1 : 1;
    }

    private int calculateTotalWeeks(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
            if (totalDays > 0) {
                return Math.max(1, (int) (totalDays / 7) + 1);
            }
        }
        return 12; // Mặc định 12 tuần
    }

    private void validateWeekNumber(LocalDate startDate, Integer weekNumber) {
        if (weekNumber == null || weekNumber < 1) {
            throw new BadRequestException("Số tuần báo cáo không hợp lệ");
        }
        int currentWeek = calculateCurrentWeek(startDate);
        if (weekNumber > currentWeek + 1) {
            throw new BadRequestException("Bạn chưa thể nộp báo cáo cho tuần trong tương lai (Tuần hiện tại: " + currentWeek + ")");
        }
    }

    private void checkIfMentorAlreadyPublished(String internCode, Integer weekNumber) {
        Optional<InternWeeklyAssessment> assessmentOpt = assessmentRepository.findByInternCodeAndWeekNumber(internCode, weekNumber);
        if (assessmentOpt.isPresent() && assessmentOpt.get().getStatus() == InternWeeklyAssessment.AssessmentStatus.PUBLISHED) {
            throw new BadRequestException("Báo cáo tuần " + weekNumber + " đã được Mentor đánh giá và công bố điểm, không thể chỉnh sửa");
        }
    }

    private void dispatchSubmitNotification(InternProfile intern, Integer weekNumber, Long reportId, Long userId) {
        try {
            CreateNotificationInternalRequest notif = CreateNotificationInternalRequest.builder()
                    .recipientId(intern.getMentorId())
                    .actorId(userId)
                    .title("Báo cáo tuần mới từ Thực tập sinh")
                    .content(String.format("TTS %s đã nộp Báo cáo Tuần %d. Vui lòng xem xét và nghiệm thu tiến độ!",
                            intern.getFullName(), weekNumber))
                    .type("REPORT_SUBMITTED")
                    .referenceType("WEEKLY_REPORT")
                    .referenceId(String.valueOf(reportId))
                    .actionUrl("/mentor/weekly-reports?internCode=" + intern.getInternCode() + "&weekNumber=" + weekNumber)
                    .build();
            notificationEventDispatcher.dispatch(notif);
        } catch (Exception e) {
            log.error("Loi khi ban notification cho Mentor khi TTS nop bao cao tuan {}: {}", weekNumber, e.getMessage());
        }
    }

    private WeeklyReportDetailResponse mapToDetailResponse(InternWeeklyReport report, InternProfile intern) {
        // Lấy đánh giá của Mentor nếu có
        Optional<InternWeeklyAssessment> assessmentOpt = assessmentRepository
                .findByInternCodeAndWeekNumber(report.getInternCode(), report.getWeekNumber());

        WeeklyReportDetailResponse.MentorAssessmentSummary mentorSummary = null;
        String displayStatus = report.getStatus() == WeeklyReportStatus.DRAFT ? "Bản nháp" :
                (report.getStatus() == WeeklyReportStatus.REVISION_REQUESTED ? "Yêu cầu chỉnh sửa lại" : "Đã nộp, chờ đánh giá");

        if (assessmentOpt.isPresent() && assessmentOpt.get().getStatus() == InternWeeklyAssessment.AssessmentStatus.PUBLISHED) {
            InternWeeklyAssessment a = assessmentOpt.get();
            displayStatus = "Đã được đánh giá";
            mentorSummary = WeeklyReportDetailResponse.MentorAssessmentSummary.builder()
                    .id(a.getId())
                    .mentorId(a.getMentorId())
                    .mentorName(a.getMentorName())
                    .technicalScore(a.getTechnicalScore())
                    .attitudeScore(a.getAttitudeScore())
                    .teamworkScore(a.getTeamworkScore())
                    .productivityScore(a.getProductivityScore())
                    .averageScore(a.getAverageScore())
                    .feedback(a.getFeedback())
                    .nextWeekGoals(a.getNextWeekGoals())
                    .publishedAt(a.getPublishedAt())
                    .build();
        }

        List<WeeklyReportDetailResponse.ReportTaskItemResponse> taskResponses = report.getTasks().stream()
                .map(t -> WeeklyReportDetailResponse.ReportTaskItemResponse.builder()
                        .id(t.getId())
                        .missionItemId(t.getMissionItemId())
                        .taskTitle(t.getTaskTitle())
                        .taskStatus(t.getTaskStatus())
                        .submissionUrl(t.getSubmissionUrl())
                        .note(t.getNote())
                        .isCompleted(t.getIsCompleted())
                        .build())
                .collect(Collectors.toList());

        return WeeklyReportDetailResponse.builder()
                .id(report.getId())
                .internCode(report.getInternCode())
                .mentorId(report.getMentorId())
                .mentorName(intern.getMentorName())
                .weekNumber(report.getWeekNumber())
                .reportDate(report.getReportDate())
                .status(report.getStatus().name())
                .statusDisplayName(displayStatus)
                .completedTasksSummary(report.getCompletedTasksSummary())
                .unfinishedTasksSummary(report.getUnfinishedTasksSummary())
                .difficultiesAndChallenges(report.getDifficultiesAndChallenges())
                .learningsAndKnowledge(report.getLearningsAndKnowledge())
                .nextWeekPlan(report.getNextWeekPlan())
                .reportAttachmentUrl(report.getReportAttachmentUrl())
                .revisionNote(report.getRevisionNote())
                .submittedAt(report.getSubmittedAt())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .tasks(taskResponses)
                .mentorAssessment(mentorSummary)
                .build();
    }

    private String getStatusDisplayName(MissionItemStatus status) {
        if (status == null) return "Chưa làm";
        return switch (status) {
            case TODO -> "Chưa làm";
            case IN_PROGRESS -> "Đang làm";
            case COMPLETED -> "Hoàn thiện";
        };
    }
}
