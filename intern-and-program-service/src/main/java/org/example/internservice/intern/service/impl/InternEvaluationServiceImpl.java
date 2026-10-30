package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.example.internservice.contract.entity.Contract;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;
import org.example.internservice.contract.repository.ContractRevisionRepository;
import org.example.internservice.contract.repository.DynamicContractRepository;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.IdentityServiceClient;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.dto.HrApproveEvaluationRequest;
import org.example.internservice.intern.dto.HrEvaluationSummaryItem;
import org.example.internservice.intern.dto.InternEvaluationRequest;
import org.example.internservice.intern.dto.InternEvaluationResponse;
import org.example.internservice.intern.entity.InternEvaluation;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.InternWeeklyAssessment;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternEvaluationRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.InternWeeklyAssessmentRepository;
import org.example.internservice.intern.service.InternEvaluationService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternEvaluationServiceImpl implements InternEvaluationService {

    private final InternEvaluationRepository evaluationRepository;
    private final InternWeeklyAssessmentRepository weeklyAssessmentRepository;
    private final InternProfileRepository internProfileRepository;
    private final NotificationEventDispatcher notificationEventDispatcher;
    private final IdentityServiceClient identityServiceClient;
    private final DynamicContractRepository dynamicContractRepository;
    private final ContractRevisionRepository contractRevisionRepository;
    private final AttendanceRepository attendanceRepository;

    @Override
    @Transactional
    public InternEvaluationResponse hrApproveEvaluation(
            String internCode,
            HrApproveEvaluationRequest request,
            String hrUsername
    ) {
        log.info("HR {} phê duyệt đánh giá cuối kỳ cho TTS: {}", hrUsername, internCode);
        InternEvaluation evaluation = evaluationRepository.findByInternCodeAndEvaluationType(internCode, "FINAL")
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiếu đánh giá cuối kỳ của thực tập sinh: " + internCode));

        if (!"SUBMITTED".equalsIgnoreCase(evaluation.getStatus()) && !"APPROVED".equalsIgnoreCase(evaluation.getStatus())) {
            throw new BadRequestException(
                    "Phiếu đánh giá chưa được Mentor gửi nộp chính thức (Trạng thái hiện tại: " + evaluation.getStatus() + ")");
        }

        // Cập nhật trạng thái duyệt của HR
        evaluation.setStatus("APPROVED");
        evaluation.setApprovedBy(hrUsername);
        evaluation.setApprovedAt(LocalDateTime.now());
        evaluation.setHrComments(request.getHrComments());
        if (request.getInternshipResult() != null && !request.getInternshipResult().isBlank()) {
            evaluation.setInternshipResult(request.getInternshipResult().trim().toUpperCase());
        }

        InternEvaluation saved = evaluationRepository.save(evaluation);

        // Cascade: Cập nhật trạng thái của InternProfile sang COMPLETED, thanh lý Hợp đồng, khóa tài khoản và bắn thông báo
        internProfileRepository.findByInternCode(internCode).ifPresent(profile -> {
            profile.setStatus(InternStatus.COMPLETED);
            internProfileRepository.save(profile);
            log.info("Đã chuyển trạng thái hồ sơ TTS {} sang COMPLETED thành công", internCode);

            // Tự động thanh lý / đóng Hợp đồng thực tập (chuyển sang EXPIRED)
            try {
                List<Contract> contracts = dynamicContractRepository.findAllByOrderByCreatedAtDesc().stream()
                        .filter(c -> c.getInternProfile() != null && c.getInternProfile().getId().equals(profile.getId()))
                        .filter(c -> c.getStatus() == DynamicContractStatus.ACTIVE 
                                || c.getStatus() == DynamicContractStatus.SIGNED
                                || c.getStatus() == DynamicContractStatus.WAITING_EFFECTIVE_DATE)
                        .collect(Collectors.toList());

                for (Contract contract : contracts) {
                    contract.setStatus(DynamicContractStatus.EXPIRED);
                    dynamicContractRepository.save(contract);
                    if (contract.getCurrentRevisionId() != null) {
                        contractRevisionRepository.findById(contract.getCurrentRevisionId()).ifPresent(rev -> {
                            rev.setStatus(DynamicContractStatus.EXPIRED);
                            contractRevisionRepository.save(rev);
                        });
                    }
                    log.info("Đã tự động chuyển trạng thái Hợp đồng ID={} ({}) sang EXPIRED do hoàn thành thực tập",
                            contract.getId(), contract.getContractNumber());
                }
            } catch (Exception e) {
                log.warn("Cảnh báo: Không thể tự động chuyển hợp đồng sang EXPIRED cho TTS {}: {}", internCode, e.getMessage());
            }

            // Tự động khóa cưỡng chế tài khoản đăng nhập (Offboarding Access Control) qua IdentityServiceClient
            if (profile.getUserId() != null) {
                try {
                    boolean locked = identityServiceClient.forceLockUserAccount(
                            profile.getUserId(),
                            "Hoàn thành kỳ thực tập (Offboarding) - Mã hồ sơ: " + internCode
                    );
                    if (locked) {
                        log.warn("Đã tự động khóa cưỡng chế tài khoản userId={} cho cựu TTS {} thành công", profile.getUserId(), internCode);
                    } else {
                        log.warn("Không thể tự động khóa tài khoản userId={} cho cựu TTS {}", profile.getUserId(), internCode);
                    }
                } catch (Exception e) {
                    log.error("Lỗi khi gọi khóa tài khoản userId={} cho TTS {}: {}", profile.getUserId(), internCode, e.getMessage());
                }
            }

            String resultText = saved.getInternshipResult() != null ? saved.getInternshipResult() : "ĐẠT";

            // 1. Thông báo cho TTS: HR đã duyệt kết quả thực tập chính thức
            if (profile.getUserId() != null) {
                CreateNotificationInternalRequest notifIntern = CreateNotificationInternalRequest.builder()
                        .recipientId(profile.getUserId())
                        .actorId(null)
                        .title("Kết quả thực tập đã được phê duyệt")
                        .content(String.format("Chúc mừng! Đánh giá hoàn thành thực tập của bạn đã được HR phê duyệt chính thức (Xếp loại: %s, Điểm tổng kết: %s/10).",
                                resultText, saved.getFinalScore()))
                        .type("FINAL_EVALUATION_APPROVED")
                        .referenceType("EVALUATION")
                        .referenceId(String.valueOf(saved.getId()))
                        .actionUrl("/profile?tab=evaluation")
                        .build();
                notificationEventDispatcher.dispatch(notifIntern);
            }

            // 2. Thông báo cho Mentor: HR đã xác nhận phiếu đánh giá do Mentor nộp
            if (profile.getMentorId() != null) {
                CreateNotificationInternalRequest notifMentor = CreateNotificationInternalRequest.builder()
                        .recipientId(profile.getMentorId())
                        .actorId(null)
                        .title("HR đã phê duyệt đánh giá thực tập")
                        .content(String.format("Phiếu đánh giá thực tập của TTS %s (%s) do bạn hướng dẫn đã được HR %s phê duyệt hoàn tất.",
                                profile.getFullName(), internCode, hrUsername))
                        .type("FINAL_EVALUATION_APPROVED")
                        .referenceType("EVALUATION")
                        .referenceId(String.valueOf(saved.getId()))
                        .actionUrl("/mentor/final-evaluations?internCode=" + internCode)
                        .build();
                notificationEventDispatcher.dispatch(notifMentor);
            }
        });

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HrEvaluationSummaryItem> getHrEvaluationSummary(
            Long programId,
            String university,
            String status,
            String keyword
    ) {
        // Lấy danh sách toàn bộ TTS đang thực tập hoặc đã hoàn thành
        List<InternProfile> internProfiles = internProfileRepository.findAll();

        List<HrEvaluationSummaryItem> result = new ArrayList<>();

        for (InternProfile p : internProfiles) {
            // Lọc theo programId nếu có
            if (programId != null && (p.getProgram() == null || !programId.equals(p.getProgram().getId()))) {
                continue;
            }
            // Lọc theo university nếu có
            if (university != null && !university.isBlank() && (p.getUniversity() == null || !p.getUniversity().toLowerCase().contains(university.toLowerCase().trim()))) {
                continue;
            }
            // Lọc theo từ khóa keyword nếu có
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase().trim();
                boolean matchName = p.getFullName() != null && p.getFullName().toLowerCase().contains(kw);
                boolean matchCode = p.getInternCode() != null && p.getInternCode().toLowerCase().contains(kw);
                boolean matchEmail = p.getEmail() != null && p.getEmail().toLowerCase().contains(kw);
                if (!matchName && !matchCode && !matchEmail) {
                    continue;
                }
            }

            // Tìm phiếu đánh giá FINAL của bạn này
            InternEvaluation eval = evaluationRepository.findByInternCodeAndEvaluationType(p.getInternCode(), "FINAL")
                    .orElse(null);

            String evalStatus = eval != null ? eval.getStatus() : "NOT_STARTED";

            // Lọc theo status nếu có
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                if ("PENDING_MENTOR".equalsIgnoreCase(status) && !"DRAFT".equalsIgnoreCase(evalStatus) && !"NOT_STARTED".equalsIgnoreCase(evalStatus)) {
                    continue;
                }
                if ("PENDING_HR".equalsIgnoreCase(status) && !"SUBMITTED".equalsIgnoreCase(evalStatus)) {
                    continue;
                }
                if ("APPROVED".equalsIgnoreCase(status) && !"APPROVED".equalsIgnoreCase(evalStatus)) {
                    continue;
                }
            }

            result.add(HrEvaluationSummaryItem.builder()
                    .evaluationId(eval != null ? eval.getId() : null)
                    .internId(p.getId())
                    .internCode(p.getInternCode())
                    .internName(p.getFullName())
                    .email(p.getEmail())
                    .university(p.getUniversity())
                    .appliedPosition(p.getAppliedPosition())
                    .programId(p.getProgram() != null ? p.getProgram().getId() : null)
                    .programName(p.getProgram() != null ? p.getProgram().getName() : "Chưa phân chương trình")
                    .departmentId(p.getProgram() != null && p.getProgram().getDepartment() != null ? p.getProgram().getDepartment().getId() : null)
                    .departmentName(p.getProgram() != null && p.getProgram().getDepartment() != null ? p.getProgram().getDepartment().getName() : "Chưa có phòng ban")
                    .mentorId(p.getMentorId())
                    .mentorName(p.getMentorName())
                    .evaluationStatus(evalStatus)
                    .technicalScore(eval != null ? eval.getTechnicalScore() : null)
                    .attitudeScore(eval != null ? eval.getAttitudeScore() : null)
                    .softSkillsScore(eval != null ? eval.getSoftSkillsScore() : null)
                    .weeklyAssessmentAvgScore(eval != null ? eval.getWeeklyAssessmentAvgScore() : null)
                    .finalScore(eval != null ? eval.getFinalScore() : null)
                    .recommendation(eval != null ? eval.getRecommendation() : null)
                    .recommendationNote(eval != null ? eval.getRecommendationNote() : null)
                    .strengths(eval != null ? eval.getStrengths() : null)
                    .areasForImprovement(eval != null ? eval.getAreasForImprovement() : null)
                    .hrComments(eval != null ? eval.getHrComments() : null)
                    .hrApprovedBy(eval != null ? eval.getApprovedBy() : null)
                    .hrApprovedAt(eval != null ? eval.getApprovedAt() : null)
                    .internshipResult(eval != null ? eval.getInternshipResult() : null)
                    .internStatus(p.getStatus() != null ? p.getStatus().name() : null)
                    .build());
        }

        return result;
    }

    @Override
    @Transactional
    public InternEvaluationResponse saveOrUpdateEvaluation(String internCode, InternEvaluationRequest request) {
        log.info("Saving {} evaluation for intern: {}", request.getEvaluationType(), internCode);

        // Tính điểm tổng kết: Công thức trung bình có trọng số (Technical 40%, Attitude 35%, SoftSkills 25%)
        BigDecimal techWeight = request.getTechnicalScore().multiply(BigDecimal.valueOf(0.40));
        BigDecimal attWeight = request.getAttitudeScore().multiply(BigDecimal.valueOf(0.35));
        BigDecimal softWeight = request.getSoftSkillsScore().multiply(BigDecimal.valueOf(0.25));
        BigDecimal finalScore = techWeight.add(attWeight).add(softWeight).setScale(1, RoundingMode.HALF_UP);

        // Snapshot điểm trung bình các tuần đánh giá (nếu có)
        List<InternWeeklyAssessment> weeklyAssessments = weeklyAssessmentRepository.findByInternCodeOrderByWeekNumberDesc(internCode);
        BigDecimal weeklyAvg = null;
        if (!weeklyAssessments.isEmpty()) {
            double avg = weeklyAssessments.stream()
                    .mapToDouble(a -> a.getAverageScore().doubleValue())
                    .average()
                    .orElse(0.0);
            weeklyAvg = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
        }

        String type = request.getEvaluationType() != null ? request.getEvaluationType().toUpperCase() : "FINAL";

        InternEvaluation evaluation = evaluationRepository
                .findByInternCodeAndEvaluationType(internCode, type)
                .orElse(InternEvaluation.builder()
                        .internCode(internCode)
                        .mentorId(1L)
                        .mentorName("Mentor Phụ Trách")
                        .evaluationType(type)
                        .build());

        evaluation.setTechnicalScore(request.getTechnicalScore());
        evaluation.setTechnicalComments(request.getTechnicalComments());
        evaluation.setAttitudeScore(request.getAttitudeScore());
        evaluation.setAttitudeComments(request.getAttitudeComments());
        evaluation.setSoftSkillsScore(request.getSoftSkillsScore());
        evaluation.setFinalScore(finalScore);
        if (weeklyAvg != null) {
            evaluation.setWeeklyAssessmentAvgScore(weeklyAvg);
        }
        evaluation.setStrengths(request.getStrengths());
        evaluation.setAreasForImprovement(request.getAreasForImprovement());
        evaluation.setRecommendation(request.getRecommendation());
        evaluation.setRecommendationNote(request.getRecommendationNote());

        if (Boolean.TRUE.equals(request.getIsSubmit())) {
            evaluation.setStatus("SUBMITTED");
            evaluation.setSubmittedAt(LocalDateTime.now());
        } else {
            if (!"SUBMITTED".equals(evaluation.getStatus()) && !"APPROVED".equals(evaluation.getStatus())) {
                evaluation.setStatus("DRAFT");
            }
        }

        InternEvaluation saved = evaluationRepository.save(evaluation);

        // Bắn thông báo cho bộ phận HR khi Mentor gửi nộp (SUBMIT) đánh giá tốt nghiệp
        if (Boolean.TRUE.equals(request.getIsSubmit())) {
            internProfileRepository.findByInternCode(internCode).ifPresent(profile -> {
                List<Long> hrUserIds = identityServiceClient.findUserIdsByRole("HR");
                if (hrUserIds.isEmpty()) {
                    hrUserIds = identityServiceClient.findUserIdsByRole("ADMIN");
                }
                String mentorName = saved.getMentorName() != null ? saved.getMentorName() : "Mentor phụ trách";
                notificationEventDispatcher.dispatchToMultiple(hrUserIds, hrId -> CreateNotificationInternalRequest.builder()
                        .recipientId(hrId)
                        .actorId(saved.getMentorId())
                        .title("Đánh giá tốt nghiệp thực tập mới")
                        .content(String.format("%s đã hoàn tất phiếu đánh giá tốt nghiệp cho TTS %s (%s, Điểm: %s/10). Vui lòng xem xét phê duyệt!",
                                mentorName, profile.getFullName(), internCode, saved.getFinalScore()))
                        .type("FINAL_EVALUATION_SUBMITTED")
                        .referenceType("EVALUATION")
                        .referenceId(String.valueOf(saved.getId()))
                        .actionUrl("/hr/evaluations?internCode=" + internCode)
                        .build());
            });
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InternEvaluationResponse getEvaluationByType(String internCode, String evaluationType) {
        String type = evaluationType != null ? evaluationType.toUpperCase() : "FINAL";
        return evaluationRepository.findByInternCodeAndEvaluationType(internCode, type)
                .map(this::mapToResponse)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternEvaluationResponse> getAllEvaluationsByIntern(String internCode) {
        return evaluationRepository.findByInternCode(internCode)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private InternEvaluationResponse mapToResponse(InternEvaluation entity) {
        int lateCount = 0;
        int earlyLeaveCount = 0;
        int absentCount = 0;
        double totalWorkingHours = 0.0;

        if (entity.getInternCode() != null) {
            var profileOpt = internProfileRepository.findByInternCode(entity.getInternCode());
            if (profileOpt.isPresent()) {
                List<Attendance> attendances = attendanceRepository.findByInternId(profileOpt.get().getId());
                for (Attendance att : attendances) {
                    if (att.getStatus() == AttendanceStatus.LATE) {
                        lateCount++;
                    } else if (att.getStatus() == AttendanceStatus.EARLY_LEAVE) {
                        earlyLeaveCount++;
                    } else if (att.getStatus() == AttendanceStatus.LATE_AND_EARLY_LEAVE) {
                        lateCount++;
                        earlyLeaveCount++;
                    } else if (att.getStatus() == AttendanceStatus.ABSENT) {
                        absentCount++;
                    }

                    if (att.getTotalWorkingHours() != null) {
                        totalWorkingHours += att.getTotalWorkingHours();
                    }
                }
            }
        }

        // Lỗi 35 [P1] (EVAL-02): Bảo mật đánh giá - Ẩn đề xuất nhân sự nhạy cảm của Mentor đối với sinh viên khi chưa được HR duyệt chính thức
        boolean isApproved = "APPROVED".equalsIgnoreCase(entity.getStatus());
        boolean isStaffOrMentor = false;
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null) {
                isStaffOrMentor = auth.getAuthorities().stream().anyMatch(a -> {
                    String role = a.getAuthority();
                    return "ROLE_HR".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role) || "ROLE_MENTOR".equalsIgnoreCase(role);
                });
            }
        } catch (Exception e) {
            log.debug("Could not verify security context: {}", e.getMessage());
        }

        String maskedRecommendation = (isApproved || isStaffOrMentor) ? entity.getRecommendation() : null;
        String maskedRecommendationNote = (isApproved || isStaffOrMentor) ? entity.getRecommendationNote() : null;
        String maskedHrComments = isApproved ? entity.getHrComments() : null;
        String maskedInternshipResult = isApproved ? entity.getInternshipResult() : null;

        return InternEvaluationResponse.builder()
                .id(entity.getId())
                .internCode(entity.getInternCode())
                .mentorId(entity.getMentorId())
                .mentorName(entity.getMentorName())
                .evaluationType(entity.getEvaluationType())
                .technicalScore(entity.getTechnicalScore())
                .technicalComments(entity.getTechnicalComments())
                .attitudeScore(entity.getAttitudeScore())
                .attitudeComments(entity.getAttitudeComments())
                .softSkillsScore(entity.getSoftSkillsScore())
                .finalScore(entity.getFinalScore())
                .weeklyAssessmentAvgScore(entity.getWeeklyAssessmentAvgScore())
                .strengths(entity.getStrengths())
                .areasForImprovement(entity.getAreasForImprovement())
                .recommendation(maskedRecommendation)
                .recommendationNote(maskedRecommendationNote)
                .status(entity.getStatus())
                .submittedAt(entity.getSubmittedAt())
                .approvedAt(entity.getApprovedAt())
                .approvedBy(entity.getApprovedBy())
                .hrComments(maskedHrComments)
                .internshipResult(maskedInternshipResult)
                .lateCount(lateCount)
                .earlyLeaveCount(earlyLeaveCount)
                .absentCount(absentCount)
                .totalWorkingHours(BigDecimal.valueOf(totalWorkingHours).setScale(1, RoundingMode.HALF_UP).doubleValue())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
