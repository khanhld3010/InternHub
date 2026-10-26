package org.example.aiservice.modules.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.modules.interview.dto.request.HrEvaluationReviewRequest;
import org.example.aiservice.modules.interview.dto.request.RegradeRequest;
import org.example.aiservice.modules.interview.dto.response.EvaluationDossierResponse;
import org.example.aiservice.modules.interview.entity.*;
import org.example.aiservice.modules.interview.enums.*;
import org.example.aiservice.modules.interview.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrInterviewEvaluationService {

    private final InterviewEvaluationRepository evaluationRepository;
    private final InterviewAnswerEvaluationRepository answerEvaluationRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewScheduleRepository scheduleRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AsyncInterviewEvaluationCoordinator evaluationCoordinator;
    private final org.example.aiservice.service.AiJobEventPublisher aiJobEventPublisher;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    /**
     * Lấy hồ sơ đánh giá chi tiết (Evaluation Dossier) của phiên thi hiện hành
     */
    @Transactional(readOnly = true)
    public EvaluationDossierResponse getEvaluationDossier(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên phỏng vấn id=" + sessionId));

        InterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId()).orElse(null);

        InterviewEvaluation eval = evaluationRepository.findBySessionIdAndIsActiveTrue(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa có kết quả đánh giá cho phiên thi này"));

        List<InterviewAnswerEvaluation> answerEvals = answerEvaluationRepository.findByEvaluationId(eval.getId());
        List<InterviewAnswer> answers = answerRepository.findBySessionId(sessionId);
        Map<Long, String> answerTextMap = answers.stream()
                .collect(Collectors.toMap(InterviewAnswer::getQuestionId, a -> a.getAnswerText() != null ? a.getAnswerText() : ""));

        List<Long> qIds = answerEvals.stream().map(InterviewAnswerEvaluation::getQuestionId).toList();
        List<InterviewQuestion> questions = questionRepository.findAllById(qIds);
        Map<Long, InterviewQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(InterviewQuestion::getId, q -> q));

        List<EvaluationDossierResponse.AnswerEvaluationDetail> answerDetails = answerEvals.stream().map(ae -> {
            InterviewQuestion q = questionMap.get(ae.getQuestionId());
            return EvaluationDossierResponse.AnswerEvaluationDetail.builder()
                    .questionId(ae.getQuestionId())
                    .questionText(q != null ? q.getQuestionText() : "")
                    .category(q != null ? q.getCategory() : QuestionCategory.TECHNICAL_FOUNDATION)
                    .scoreWeight(ae.getWeight())
                    .candidateAnswer(answerTextMap.getOrDefault(ae.getQuestionId(), ""))
                    .score(ae.getScore())
                    .hrOverrideScore(ae.getHrOverrideScore())
                    .hrNotes(ae.getHrNotes())
                    .rubric(EvaluationDossierResponse.RubricScores.builder()
                            .technicalAccuracy(ae.getTechnicalAccuracyScore())
                            .optimization(ae.getOptimizationScore())
                            .articulation(ae.getArticulationScore())
                            .edgeCases(ae.getEdgeCasesScore())
                            .build())
                    .evidenceQuote(ae.getEvidenceQuote())
                    .rationale(ae.getRationale())
                    .suggestedSolution(ae.getSuggestedSolution())
                    .isFlaggedSuspicious(ae.getIsFlaggedSuspicious())
                    .securityNote(ae.getSecurityNote())
                    .build();
        }).toList();

        int blur = session.getBlurCount() != null ? session.getBlurCount() : 0;
        int paste = session.getPasteCount() != null ? session.getPasteCount() : 0;
        boolean isViolated = eval.getProctoringStatus() == ProctoringStatus.DISQUALIFIED
                || eval.getProctoringStatus() == ProctoringStatus.SUSPICIOUS;

        return EvaluationDossierResponse.builder()
                .evaluationId(eval.getId())
                .sessionId(session.getId())
                .version(eval.getVersion())
                .isActive(eval.getIsActive())
                .candidateName(session.getCandidateName())
                .candidateEmail(session.getCandidateEmail())
                .scheduleTitle(schedule != null ? schedule.getTitle() : "Lịch phỏng vấn AI")
                .gradingStatus(eval.getGradingStatus())
                .totalScore(eval.getTotalScore())
                .hrFinalTotalScore(eval.getHrFinalTotalScore())
                .aiRecommendation(eval.getAiRecommendation())
                .proctoringStatus(eval.getProctoringStatus())
                .aiSummary(eval.getAiSummary())
                .strengthsSummary(eval.getStrengthsSummary())
                .weaknessesSummary(eval.getWeaknessesSummary())
                .modelName(eval.getModelName())
                .promptVersion(eval.getPromptVersion())
                .regradeReason(eval.getRegradeReason())
                .retryCount(eval.getRetryCount())
                .errorMessage(eval.getErrorMessage())
                .antiCheatInfo(EvaluationDossierResponse.AntiCheatInfo.builder()
                        .blurCount(blur)
                        .pasteCount(paste)
                        .isViolated(isViolated)
                        .proctoringStatus(eval.getProctoringStatus())
                        .details(eval.getAntiCheatAnalysis())
                        .build())
                .hrReview(EvaluationDossierResponse.HrReviewInfo.builder()
                        .status(eval.getHrStatus())
                        .hrFinalTotalScore(eval.getHrFinalTotalScore())
                        .notes(eval.getHrNotes())
                        .reviewedBy(eval.getReviewedByHrId())
                        .reviewedAt(eval.getReviewedAt())
                        .build())
                .answers(answerDetails)
                .build();
    }

    /**
     * HR phê duyệt hoặc lưu nháp kết quả đánh giá (hỗ trợ ghi đè điểm từng câu)
     */
    @Transactional
    public EvaluationDossierResponse reviewEvaluation(Long sessionId, HrEvaluationReviewRequest request, String hrUserId) {
        InterviewEvaluation eval = evaluationRepository.findBySessionIdAndIsActiveTrue(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá hiện hành cho session=" + sessionId));

        List<InterviewAnswerEvaluation> answerEvals = answerEvaluationRepository.findByEvaluationId(eval.getId());
        Map<Long, InterviewAnswerEvaluation> answerEvalMap = answerEvals.stream()
                .collect(Collectors.toMap(InterviewAnswerEvaluation::getQuestionId, ae -> ae));

        // Áp dụng ghi đè điểm từng câu hỏi nếu có
        if (request.getQuestionOverrides() != null) {
            for (HrEvaluationReviewRequest.QuestionOverrideItem item : request.getQuestionOverrides()) {
                InterviewAnswerEvaluation target = answerEvalMap.get(item.getQuestionId());
                if (target != null) {
                    if (item.getOverrideScore() != null) {
                        BigDecimal clamped = item.getOverrideScore().max(BigDecimal.ZERO).min(BigDecimal.valueOf(100.0));
                        target.setHrOverrideScore(clamped.setScale(2, RoundingMode.HALF_UP));
                    }
                    if (item.getHrNotes() != null) {
                        target.setHrNotes(item.getHrNotes());
                    }
                    answerEvaluationRepository.save(target);
                }
            }
        }

        // Tính toán lại HR Final Total Score dựa trên các điểm ghi đè
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        boolean hasAnyOverride = false;

        for (InterviewAnswerEvaluation ae : answerEvals) {
            BigDecimal effectiveScore = ae.getHrOverrideScore() != null ? ae.getHrOverrideScore() : ae.getScore();
            if (ae.getHrOverrideScore() != null) {
                hasAnyOverride = true;
            }
            BigDecimal w = ae.getWeight() != null && ae.getWeight().compareTo(BigDecimal.ZERO) >= 0 ? ae.getWeight() : BigDecimal.ZERO;
            totalWeight = totalWeight.add(w);
            weightedSum = weightedSum.add(effectiveScore.multiply(w));
        }

        BigDecimal calculatedHrScore;
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            calculatedHrScore = weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal sum = BigDecimal.ZERO;
            for (InterviewAnswerEvaluation ae : answerEvals) {
                sum = sum.add(ae.getHrOverrideScore() != null ? ae.getHrOverrideScore() : ae.getScore());
            }
            calculatedHrScore = sum.divide(BigDecimal.valueOf(answerEvals.size()), 2, RoundingMode.HALF_UP);
        }

        eval.setHrFinalTotalScore(hasAnyOverride ? calculatedHrScore : eval.getTotalScore());
        eval.setHrStatus(request.getHrStatus());
        eval.setHrNotes(request.getNotes());
        eval.setReviewedByHrId(hrUserId);
        eval.setReviewedAt(LocalDateTime.now());
        eval.setUpdatedAt(LocalDateTime.now());

        evaluationRepository.save(eval);

        log.info("HR {} đã review evaluationId={}, status={}, hrFinalScore={}", hrUserId, eval.getId(), request.getHrStatus(), eval.getHrFinalTotalScore());

        return getEvaluationDossier(sessionId);
    }

    /**
     * Hủy phê duyệt để đưa hrStatus về PENDING_REVIEW (mở khóa sửa điểm / chấm lại)
     */
    @Transactional
    public EvaluationDossierResponse revokeApproval(Long sessionId, String hrUserId) {
        InterviewEvaluation eval = evaluationRepository.findBySessionIdAndIsActiveTrue(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá cho session=" + sessionId));

        eval.setHrStatus(HrEvaluationStatus.PENDING_REVIEW);
        eval.setUpdatedAt(LocalDateTime.now());
        evaluationRepository.save(eval);

        log.info("HR {} đã hủy phê duyệt (Revoke) cho evaluationId={}", hrUserId, eval.getId());
        return getEvaluationDossier(sessionId);
    }

    /**
     * Kích hoạt chấm lại bài thi bằng AI (Tạo phiên bản mới Versioning)
     */
    @Transactional
    public Map<String, Object> triggerRegrade(Long sessionId, RegradeRequest request, String hrUserId) {
        InterviewEvaluation currentEval = evaluationRepository.findBySessionIdAndIsActiveTrue(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá cho session=" + sessionId));

        if (currentEval.getHrStatus() == HrEvaluationStatus.APPROVED || currentEval.getHrStatus() == HrEvaluationStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hồ sơ đã được chốt duyệt. Vui lòng bấm 'Hủy phê duyệt' trước khi kích hoạt chấm lại.");
        }

        // Hủy kích hoạt bản ghi hiện tại
        currentEval.setIsActive(false);
        evaluationRepository.save(currentEval);

        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        // 1. Redis Distributed Lock SETNX chống spam click (TTL 60s)
        String lockKey = "ai:lock:regrade:" + sessionId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", java.time.Duration.ofSeconds(60));
        if (Boolean.FALSE.equals(acquired)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài thi đang trong tiến trình AI thẩm định, vui lòng đợi.");
        }

        try {
            int nextVersion = currentEval.getVersion() + 1;
            String jobId = java.util.UUID.randomUUID().toString();
            Long userId = null;
            if (hrUserId != null && !hrUserId.trim().isEmpty()) {
                try {
                    userId = Long.parseLong(hrUserId.trim());
                } catch (NumberFormatException nfe) {
                    log.warn("hrUserId '{}' không phải là định dạng số, sử dụng mặc định 0L cho hệ thống", hrUserId);
                    userId = 0L;
                }
            } else {
                userId = 0L;
            }

            // Tạo bản ghi mới cho lượt chấm này
            InterviewEvaluation newEval = InterviewEvaluation.builder()
                    .sessionId(sessionId)
                    .version(nextVersion)
                    .isActive(true)
                    .gradingStatus(GradingStatus.PENDING)
                    .hrStatus(HrEvaluationStatus.PENDING_REVIEW)
                    .proctoringStatus(evaluationCoordinator.calculateProctoringStatus(session))
                    .modelName("gemini-1.5-pro")
                    .regradeReason(request.getReason())
                    .requestedBy(hrUserId)
                    .retryCount(0)
                    .build();

            InterviewEvaluation saved = evaluationRepository.save(newEval);

            // Bắn sự kiện QUEUED lên Redis
            aiJobEventPublisher.publishQueued(jobId, userId, org.example.aiservice.dto.response.AiJobType.INTERVIEW_REGRADE,
                    String.valueOf(sessionId), "Đã tiếp nhận yêu cầu chấm lại bài thi...");

            // Kích hoạt chấm điểm ngầm cho phiên bản mới kèm jobId
            evaluationCoordinator.coordinateEvaluationAsync(sessionId, "HR_MANUAL_REGRADE", request.getReason(), jobId, userId);

            log.info("HR {} kích hoạt Regrade session={}, newVersion={}, jobId={}", hrUserId, sessionId, nextVersion, jobId);

            Map<String, Object> resp = new HashMap<>();
            resp.put("jobId", jobId);
            resp.put("sessionId", sessionId);
            resp.put("newEvaluationId", saved.getId());
            resp.put("newVersion", nextVersion);
            resp.put("status", "QUEUED");
            resp.put("gradingStatus", GradingStatus.PENDING);
            resp.put("message", "Đã tiếp nhận yêu cầu chấm lại phiên thi. Hệ thống đang tiến hành xử lý ngầm.");

            return resp;
        } catch (Exception ex) {
            // Giải phóng khóa ngay lập tức nếu khởi tạo thất bại để không giam giữ người dùng
            redisTemplate.delete(lockKey);
            log.error("Khởi tạo tiến trình Regrade thất bại cho session={}, đã giải phóng lockKey: {}", sessionId, ex.getMessage(), ex);
            throw ex;
        }
    }
}
