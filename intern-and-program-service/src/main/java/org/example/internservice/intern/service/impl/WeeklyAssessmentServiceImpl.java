package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.dto.request.RequestReportRevisionRequest;
import org.example.internservice.intern.dto.request.WeeklyAssessmentRequest;
import org.example.internservice.intern.dto.response.MentorTriageOverviewResponse;
import org.example.internservice.intern.dto.response.MentorWeeklyReportReviewResponse;
import org.example.internservice.intern.dto.response.ReportRevisionResponse;
import org.example.internservice.intern.dto.response.WeeklyAssessmentResponse;
import org.example.internservice.intern.dto.response.WeeklyReportDetailResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.InternWeeklyAssessment;
import org.example.internservice.intern.entity.InternWeeklyReport;
import org.example.internservice.intern.entity.enums.WeeklyReportStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyAssessmentRepository;
import org.example.internservice.intern.repository.InternWeeklyReportRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.intern.service.WeeklyAssessmentService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WeeklyAssessmentServiceImpl implements WeeklyAssessmentService {

    private final InternWeeklyAssessmentRepository assessmentRepository;
    private final InternWeeklyReportRepository reportRepository;
    private final InternProfileRepository internProfileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final NotificationEventDispatcher notificationEventDispatcher;

    @Override
    @Transactional
    public WeeklyAssessmentResponse saveAssessment(String internCode, WeeklyAssessmentRequest request, Long mentorId, String mentorName) {
        return saveAssessment(internCode, request, mentorId, mentorName, null);
    }

    @Override
    @Transactional
    public WeeklyAssessmentResponse saveAssessment(String internCode, WeeklyAssessmentRequest request, Long mentorId, String mentorName, String role) {
        log.info("Luu danh gia tuan {} cho TTS: {} boi Mentor ID: {}", request.getWeekNumber(), internCode, mentorId);

        InternProfile internProfile = internProfileRepository.findByInternCode(internCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực tập sinh với mã: " + internCode));

        validateMentorAccess(internProfile, mentorId, role);

        // Tính điểm trung bình chuẩn (làm tròn 1 chữ số thập phân)
        BigDecimal sum = BigDecimal.valueOf(
                request.getTechnicalScore() + request.getAttitudeScore() +
                request.getTeamworkScore() + request.getProductivityScore()
        );
        BigDecimal averageScore = sum.divide(BigDecimal.valueOf(4), 1, RoundingMode.HALF_UP);

        boolean isPublish = Boolean.TRUE.equals(request.getIsPublish());
        InternWeeklyAssessment.AssessmentStatus targetStatus = isPublish
                ? InternWeeklyAssessment.AssessmentStatus.PUBLISHED
                : InternWeeklyAssessment.AssessmentStatus.DRAFT;

        // Tìm bản ghi đánh giá tuần hiện tại nếu đã có thì cập nhật, chưa có thì tạo mới
        InternWeeklyAssessment assessment = assessmentRepository
                .findByInternCodeAndWeekNumber(internCode, request.getWeekNumber())
                .orElse(InternWeeklyAssessment.builder()
                        .internCode(internCode)
                        .weekNumber(request.getWeekNumber())
                        .build());

        assessment.setMentorId(mentorId != null ? mentorId : 0L);
        assessment.setMentorName(mentorName != null ? mentorName : internProfile.getMentorName());
        assessment.setAssessmentDate(LocalDate.now());
        assessment.setTechnicalScore(request.getTechnicalScore());
        assessment.setAttitudeScore(request.getAttitudeScore());
        assessment.setTeamworkScore(request.getTeamworkScore());
        assessment.setProductivityScore(request.getProductivityScore());
        assessment.setAverageScore(averageScore);
        assessment.setFeedback(request.getFeedback());
        assessment.setNextWeekGoals(request.getNextWeekGoals());
        assessment.setStatus(targetStatus);

        if (isPublish) {
            assessment.setPublishedAt(LocalDateTime.now());
        }

        InternWeeklyAssessment saved = assessmentRepository.save(assessment);
        log.info("Luu thanh cong danh gia tuan {} (status: {}) cho TTS: {}", saved.getWeekNumber(), saved.getStatus(), internCode);

        // Khi Mentor PUBLISH: Tự động đồng bộ chuyển InternWeeklyReport.status sang REVIEWED để khóa sửa đối với TTS
        if (isPublish) {
            reportRepository.findByInternCodeAndWeekNumber(internCode, request.getWeekNumber())
                    .ifPresent(report -> {
                        report.setStatus(WeeklyReportStatus.REVIEWED);
                        reportRepository.save(report);
                        log.info("Dong bo thanh cong bao cao tuan {} cua TTS {} sang trang thai REVIEWED", request.getWeekNumber(), internCode);
                    });

            // Bắn thông báo thời gian thực cho TTS khi Mentor công bố đánh giá tuần
            if (internProfile.getUserId() != null) {
                String senderName = mentorName != null && !mentorName.isBlank() ? mentorName : "Mentor phụ trách";
                CreateNotificationInternalRequest notif = CreateNotificationInternalRequest.builder()
                        .recipientId(internProfile.getUserId())
                        .actorId(mentorId)
                        .title("Nhận xét đánh giá tuần mới")
                        .content(String.format("%s đã công bố đánh giá Tuần %d cho bạn (Điểm TB: %s/5.0). Hãy xem phản hồi và mục tiêu tuần tới!",
                                senderName, saved.getWeekNumber(), saved.getAverageScore()))
                        .type("WEEKLY_ASSESSMENT_PUBLISHED")
                        .referenceType("WEEKLY_ASSESSMENT")
                        .referenceId(String.valueOf(saved.getId()))
                        .actionUrl("/mentor/weekly-evaluations?internCode=" + internCode)
                        .build();
                notificationEventDispatcher.dispatch(notif);
            }
        }

        return WeeklyAssessmentResponse.fromEntity(saved);
    }

    @Override
    public List<WeeklyAssessmentResponse> getAssessmentHistory(String internCode, String userRole) {
        log.info("Lay lich su danh gia tuan cua TTS: {}, role: {}", internCode, userRole);

        List<InternWeeklyAssessment> list;
        // INTERN chỉ được xem những bản ghi đã PUBLISHED
        if ("INTERN".equalsIgnoreCase(userRole)) {
            list = assessmentRepository.findByInternCodeAndStatusOrderByWeekNumberDesc(
                    internCode, InternWeeklyAssessment.AssessmentStatus.PUBLISHED
            );
        } else {
            // MENTOR / HR / ADMIN được xem tất cả (kể cả DRAFT)
            list = assessmentRepository.findByInternCodeOrderByWeekNumberDesc(internCode);
        }

        return list.stream()
                .map(WeeklyAssessmentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public MentorTriageOverviewResponse getMentorTriageOverview(Long mentorId) {
        log.info("Lay overview triage danh gia tuan cho Mentor ID: {}", mentorId);

        List<InternProfile> myInterns;
        if (mentorId != null && mentorId > 0) {
            List<Long> mentorIds = resolveMentorIds(mentorId);
            myInterns = internProfileRepository.findByMentorIdIn(mentorIds);
        } else {
            myInterns = internProfileRepository.findAll();
        }

        long totalAssigned = myInterns.size();
        long needsWeeklyAssessmentCount = 0;
        long overdueMidtermCount = 0;

        List<MentorTriageOverviewResponse.MentorInternItem> internItems = new ArrayList<>();
        BigDecimal totalScoreSum = BigDecimal.ZERO;
        int scoredCount = 0;

        for (InternProfile intern : myInterns) {
            int currentWeek = 1;
            int totalWeeks = 12;
            if (intern.getStartDate() != null) {
                long days = ChronoUnit.DAYS.between(intern.getStartDate(), LocalDate.now());
                if (days > 0) {
                    currentWeek = Math.min((int) (days / 7) + 1, totalWeeks);
                }
            }
            int progressPercent = Math.min(100, Math.max(0, (int) Math.round(((double) currentWeek / totalWeeks) * 100)));

            List<InternWeeklyAssessment> assessments = assessmentRepository.findByInternCodeOrderByWeekNumberDesc(intern.getInternCode());

            final int finalCurrentWeek = currentWeek;
            boolean hasCurrentWeekPublished = assessments.stream()
                    .anyMatch(a -> a.getWeekNumber().equals(finalCurrentWeek) && a.getStatus() == InternWeeklyAssessment.AssessmentStatus.PUBLISHED);

            String weeklyStatus = "ASSESSED";
            boolean overdueMidterm = false;

            if (currentWeek >= 6 && assessments.size() < 3) {
                weeklyStatus = "OVERDUE_MIDTERM";
                overdueMidterm = true;
                overdueMidtermCount++;
            } else if (!hasCurrentWeekPublished) {
                weeklyStatus = "NEEDS_ASSESSMENT";
                needsWeeklyAssessmentCount++;
            }

            BigDecimal lastAverageScore = null;
            if (!assessments.isEmpty()) {
                lastAverageScore = assessments.get(0).getAverageScore();
                totalScoreSum = totalScoreSum.add(lastAverageScore);
                scoredCount++;
            }

            internItems.add(MentorTriageOverviewResponse.MentorInternItem.builder()
                    .internCode(intern.getInternCode())
                    .fullName(intern.getFullName())
                    .programName(intern.getProgram() != null ? intern.getProgram().getName() : null)
                    .currentWeek(currentWeek)
                    .totalWeeks(totalWeeks)
                    .progressPercent(progressPercent)
                    .weeklyStatus(weeklyStatus)
                    .lastAverageScore(lastAverageScore)
                    .overdueMidterm(overdueMidterm)
                    .build());
        }

        BigDecimal groupAverageScore = scoredCount > 0
                ? totalScoreSum.divide(BigDecimal.valueOf(scoredCount), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return MentorTriageOverviewResponse.builder()
                .totalAssigned(totalAssigned)
                .needsWeeklyAssessmentCount(needsWeeklyAssessmentCount)
                .overdueMidtermCount(overdueMidtermCount)
                .groupAverageScore(groupAverageScore)
                .interns(internItems)
                .build();
    }

    @Override
    public MentorWeeklyReportReviewResponse getWeeklyReportForMentor(String internCode, Integer weekNumber, Long mentorId, String role) {
        log.info("Mentor ID {} lay bao cao tuan {} cua TTS {} de doi soat", mentorId, weekNumber, internCode);

        InternProfile internProfile = internProfileRepository.findByInternCode(internCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực tập sinh với mã: " + internCode));

        validateMentorAccess(internProfile, mentorId, role);

        LocalDate startDate = internProfile.getStartDate() != null ? internProfile.getStartDate() : LocalDate.now();
        LocalDate weekStart = startDate.plusDays((long) (weekNumber - 1) * 7);
        LocalDate weekEnd = weekStart.plusDays(6);

        // Nạp chi tiết Báo cáo tuần kèm snapshot tasks của TTS (JOIN FETCH)
        Optional<InternWeeklyReport> reportOpt = reportRepository.findByInternCodeAndWeekNumberWithTasks(internCode, weekNumber);
        WeeklyReportDetailResponse reportDetail = reportOpt.map(r -> mapToReportDetailResponse(r, internProfile)).orElse(null);

        // Nạp đánh giá của Mentor nếu có
        Optional<InternWeeklyAssessment> assessmentOpt = assessmentRepository.findByInternCodeAndWeekNumber(internCode, weekNumber);
        WeeklyAssessmentResponse assessmentResponse = assessmentOpt.map(WeeklyAssessmentResponse::fromEntity).orElse(null);

        return MentorWeeklyReportReviewResponse.builder()
                .internCode(internProfile.getInternCode())
                .internName(internProfile.getFullName())
                .programName(internProfile.getProgram() != null ? internProfile.getProgram().getName() : null)
                .appliedPosition(internProfile.getAppliedPosition())
                .weekNumber(weekNumber)
                .startDate(weekStart)
                .endDate(weekEnd)
                .report(reportDetail)
                .assessment(assessmentResponse)
                .build();
    }

    @Override
    @Transactional
    public ReportRevisionResponse requestReportRevision(String internCode, Integer weekNumber, RequestReportRevisionRequest request, Long mentorId, String mentorName, String role) {
        log.info("Mentor ID {} yeu cau sua lai bao cao tuan {} cho TTS {}", mentorId, weekNumber, internCode);

        InternProfile internProfile = internProfileRepository.findByInternCode(internCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực tập sinh với mã: " + internCode));

        validateMentorAccess(internProfile, mentorId, role);

        InternWeeklyReport report = reportRepository.findByInternCodeAndWeekNumber(internCode, weekNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo tuần " + weekNumber + " của thực tập sinh."));

        if (report.getStatus() != WeeklyReportStatus.SUBMITTED) {
            throw new BadRequestException("Chỉ có thể yêu cầu chỉnh sửa đối với báo cáo tuần đã được nộp chính thức.");
        }

        report.setStatus(WeeklyReportStatus.REVISION_REQUESTED);
        report.setRevisionNote(request.getRevisionNote());
        InternWeeklyReport saved = reportRepository.save(report);

        // Gửi thông báo thời gian thực đến TTS
        if (internProfile.getUserId() != null) {
            String senderName = mentorName != null && !mentorName.isBlank() ? mentorName : "Mentor phụ trách";
            CreateNotificationInternalRequest notif = CreateNotificationInternalRequest.builder()
                    .recipientId(internProfile.getUserId())
                    .actorId(mentorId)
                    .title("Yêu cầu chỉnh sửa Báo Cáo Tuần")
                    .content(String.format("%s đã yêu cầu bạn chỉnh sửa lại Báo Cáo Tuần %d. Lý do: %s",
                            senderName, weekNumber, request.getRevisionNote()))
                    .type("REPORT_REVISION_REQUESTED")
                    .referenceType("WEEKLY_REPORT")
                    .referenceId(String.valueOf(saved.getId()))
                    .actionUrl("/intern/weekly-reports")
                    .build();
            notificationEventDispatcher.dispatch(notif);
        }

        return ReportRevisionResponse.builder()
                .internCode(internCode)
                .weekNumber(weekNumber)
                .status(WeeklyReportStatus.REVISION_REQUESTED.name())
                .statusDisplayName("Yêu cầu chỉnh sửa lại")
                .revisionNote(saved.getRevisionNote())
                .build();
    }

    private void validateMentorAccess(InternProfile intern, Long mentorId, String role) {
        if (role != null && (role.contains("HR") || role.contains("ADMIN"))) {
            return;
        }
        if (mentorId != null && intern.getMentorId() != null) {
            List<Long> allowedMentorIds = resolveMentorIds(mentorId);
            if (!allowedMentorIds.contains(intern.getMentorId())) {
                throw new AccessDeniedException("Bạn không có quyền truy cập hoặc đánh giá thực tập sinh này.");
            }
        }
    }

    private List<Long> resolveMentorIds(Long mentorId) {
        if (mentorId == null) {
            return List.of();
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

    private WeeklyReportDetailResponse mapToReportDetailResponse(InternWeeklyReport report, InternProfile intern) {
        String displayStatus = switch (report.getStatus()) {
            case DRAFT -> "Bản nháp";
            case SUBMITTED -> "Đã nộp, chờ đánh giá";
            case REVISION_REQUESTED -> "Yêu cầu chỉnh sửa lại";
            case REVIEWED -> "Đã được đánh giá";
        };

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
                .build();
    }
}
