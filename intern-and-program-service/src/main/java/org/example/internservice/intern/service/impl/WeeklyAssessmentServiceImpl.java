package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.dto.request.WeeklyAssessmentRequest;
import org.example.internservice.intern.dto.response.MentorTriageOverviewResponse;
import org.example.internservice.intern.dto.response.WeeklyAssessmentResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.InternWeeklyAssessment;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyAssessmentRepository;
import org.example.internservice.intern.service.WeeklyAssessmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyAssessmentServiceImpl implements WeeklyAssessmentService {

    private final InternWeeklyAssessmentRepository assessmentRepository;
    private final InternProfileRepository internProfileRepository;

    @Override
    @Transactional
    public WeeklyAssessmentResponse saveAssessment(String internCode, WeeklyAssessmentRequest request, Long mentorId, String mentorName) {
        log.info("Luu danh gia tuan {} cho TTS: {} boi Mentor ID: {}", request.getWeekNumber(), internCode, mentorId);

        InternProfile internProfile = internProfileRepository.findByInternCode(internCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực tập sinh với mã: " + internCode));

        // Kiểm tra phân công mentor (nếu có thông tin mentorId)
        if (internProfile.getMentorId() != null && mentorId != null && !internProfile.getMentorId().equals(mentorId)) {
            log.warn("Mentor ID {} khong phai mentor truc tiep cua TTS {}", mentorId, internCode);
        }

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

        return WeeklyAssessmentResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public MentorTriageOverviewResponse getMentorTriageOverview(Long mentorId) {
        log.info("Lay overview triage danh gia tuan cho Mentor ID: {}", mentorId);

        List<InternProfile> myInterns;
        if (mentorId != null && mentorId > 0) {
            myInterns = internProfileRepository.findByMentorId(mentorId);
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
            // Tính số tuần hiện tại từ ngày bắt đầu
            int currentWeek = 1;
            int totalWeeks = 12; // Mặc định 12 tuần
            if (intern.getStartDate() != null) {
                long days = ChronoUnit.DAYS.between(intern.getStartDate(), LocalDate.now());
                if (days > 0) {
                    currentWeek = Math.min((int) (days / 7) + 1, totalWeeks);
                }
            }
            int progressPercent = Math.min(100, Math.max(0, (int) Math.round(((double) currentWeek / totalWeeks) * 100)));

            // Lấy danh sách đánh giá tuần của TTS
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
}
