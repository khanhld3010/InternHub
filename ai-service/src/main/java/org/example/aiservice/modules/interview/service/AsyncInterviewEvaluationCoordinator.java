package org.example.aiservice.modules.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.modules.interview.entity.InterviewAnswerEvaluation;
import org.example.aiservice.modules.interview.entity.InterviewEvaluation;
import org.example.aiservice.modules.interview.entity.InterviewSession;
import org.example.aiservice.modules.interview.enums.GradingStatus;
import org.example.aiservice.modules.interview.enums.HrEvaluationStatus;
import org.example.aiservice.modules.interview.enums.ProctoringStatus;
import org.example.aiservice.modules.interview.enums.SubmissionReason;
import org.example.aiservice.modules.interview.repository.InterviewAnswerEvaluationRepository;
import org.example.aiservice.modules.interview.repository.InterviewEvaluationRepository;
import org.example.aiservice.modules.interview.repository.InterviewSessionRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncInterviewEvaluationCoordinator {

    private final InterviewEvaluationRepository evaluationRepository;
    private final InterviewAnswerEvaluationRepository answerEvaluationRepository;
    private final InterviewSessionRepository sessionRepository;
    private final AiInterviewGraderService graderService;
    private final PlatformTransactionManager transactionManager;

    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_DELAYS_MS = {2000L, 4000L, 8000L};

    /**
     * Khởi tạo hoặc tìm bản ghi Evaluation hiện hành, claim job nguyên tử và chấm điểm
     */
    @Async
    public void coordinateEvaluationAsync(Long sessionId, String triggerSource, String reason) {
        log.info("[EvaluationCoordinator] Bắt đầu điều phối chấm điểm cho session={} (Nguồn kích hoạt: {})", sessionId, triggerSource);

        Optional<InterviewSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.error("[EvaluationCoordinator] Không tìm thấy session={}", sessionId);
            return;
        }
        InterviewSession session = sessionOpt.get();

        // 1. Lấy hoặc tạo bản ghi Evaluation v1 với Transaction độc lập cam kết ngay vào DB
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        InterviewEvaluation evaluation = txTemplate.execute(status -> getOrCreateEvaluation(session, reason));

        if (evaluation == null) {
            log.error("[EvaluationCoordinator] Không thể khởi tạo bản ghi evaluation cho session={}", sessionId);
            return;
        }

        // 2. Atomic Claim Job: Chống Race Condition 2 job chấm cùng phiên
        int claimed = evaluationRepository.claimGradingJob(evaluation.getId());
        if (claimed == 0) {
            log.warn("[EvaluationCoordinator] JOB_DUPLICATE_IGNORED: Phiên evaluationId={} đang được xử lý bởi luồng khác.", evaluation.getId());
            return;
        }

        log.info("[EvaluationCoordinator] Claim job thành công cho evaluationId={}. Bắt đầu gọi AI Grader Engine.", evaluation.getId());

        // 3. Thực thi chấm điểm với Exponential Backoff Retry (3 lần)
        executeGradingWithRetry(evaluation.getId(), session, txTemplate);
    }

    @Transactional
    public InterviewEvaluation getOrCreateEvaluation(InterviewSession session, String reason) {
        Optional<InterviewEvaluation> activeEvalOpt = evaluationRepository.findBySessionIdAndIsActiveTrue(session.getId());
        if (activeEvalOpt.isPresent()) {
            return activeEvalOpt.get();
        }

        // Tạo bản ghi mới Version 1
        InterviewEvaluation newEval = InterviewEvaluation.builder()
                .sessionId(session.getId())
                .version(1)
                .isActive(true)
                .gradingStatus(GradingStatus.PENDING)
                .hrStatus(HrEvaluationStatus.PENDING_REVIEW)
                .proctoringStatus(calculateProctoringStatus(session))
                .antiCheatAnalysis(formatAntiCheatAnalysis(session))
                .modelName("gemini-1.5-pro")
                .regradeReason(reason)
                .retryCount(0)
                .build();

        return evaluationRepository.save(newEval);
    }

    private void executeGradingWithRetry(Long evaluationId, InterviewSession session, TransactionTemplate txTemplate) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                log.info("[EvaluationCoordinator] Đang thực thi chấm bài cho evaluationId={}, lần thử #{}", evaluationId, attempt);
                AiInterviewGraderService.EvaluationResult result = graderService.gradeSession(session);

                // Chấm thành công -> Lưu kết quả xuống DB qua Transaction độc lập
                txTemplate.executeWithoutResult(status -> saveSuccessfulEvaluation(evaluationId, session, result));
                log.info("[EvaluationCoordinator] Chấm điểm thành công cho session={}, TotalScore={}", session.getId(), result.totalScore());
                return;

            } catch (Exception ex) {
                lastException = ex;
                log.warn("[EvaluationCoordinator] Lần thử #{} thất bại cho evaluationId={}: {}", attempt, evaluationId, ex.getMessage());

                if (attempt < MAX_RETRIES) {
                    try {
                        long sleepMs = BACKOFF_DELAYS_MS[attempt - 1];
                        log.info("[EvaluationCoordinator] Chờ {}ms trước khi retry lần tiếp theo...", sleepMs);
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Sau 3 lần thất bại hoàn toàn -> Chuyển sang FAILED_RETRYABLE
        final int finalAttempts = attempt;
        final String finalErrMsg = lastException != null ? lastException.getMessage() : "Unknown error";
        txTemplate.executeWithoutResult(status -> markEvaluationFailed(evaluationId, finalAttempts, finalErrMsg));
    }

    @Transactional
    public void saveSuccessfulEvaluation(Long evaluationId, InterviewSession session, AiInterviewGraderService.EvaluationResult result) {
        InterviewEvaluation eval = evaluationRepository.findById(evaluationId).orElse(null);
        if (eval == null) return;

        eval.setTotalScore(result.totalScore());
        eval.setAiRecommendation(result.recommendation());
        eval.setAiSummary(result.aiSummary());
        eval.setStrengthsSummary(result.strengthsSummary());
        eval.setWeaknessesSummary(result.weaknessesSummary());
        eval.setProctoringStatus(calculateProctoringStatus(session));
        eval.setAntiCheatAnalysis(formatAntiCheatAnalysis(session));
        eval.setGradingStatus(GradingStatus.COMPLETED);
        eval.setUpdatedAt(LocalDateTime.now());

        evaluationRepository.save(eval);

        // Xóa và lưu lại chi tiết từng câu hỏi
        answerEvaluationRepository.deleteByEvaluationId(evaluationId);
        for (InterviewAnswerEvaluation ansEval : result.answerEvaluations()) {
            ansEval.setEvaluationId(evaluationId);
            answerEvaluationRepository.save(ansEval);
        }
    }

    @Transactional
    public void markEvaluationFailed(Long evaluationId, int attempts, String errorMsg) {
        InterviewEvaluation eval = evaluationRepository.findById(evaluationId).orElse(null);
        if (eval == null) return;

        eval.setGradingStatus(GradingStatus.FAILED_RETRYABLE);
        eval.setRetryCount(attempts);
        eval.setErrorMessage(errorMsg);
        eval.setUpdatedAt(LocalDateTime.now());

        evaluationRepository.save(eval);
        log.error("[EvaluationCoordinator] Đã đánh dấu FAILED_RETRYABLE cho evaluationId={} sau {} lần thử. Lỗi: {}", evaluationId, attempts, errorMsg);
    }

    /**
     * Phân tích độc lập Telemetry để xác định ProctoringStatus
     */
    public ProctoringStatus calculateProctoringStatus(InterviewSession session) {
        if (session.getSubmissionReason() == SubmissionReason.STRIKE_LIMIT_EXCEEDED) {
            return ProctoringStatus.DISQUALIFIED;
        }
        int blur = session.getBlurCount() != null ? session.getBlurCount() : 0;
        int paste = session.getPasteCount() != null ? session.getPasteCount() : 0;

        if (blur >= 2 || paste >= 5) {
            return ProctoringStatus.SUSPICIOUS;
        }
        return ProctoringStatus.CLEAN;
    }

    private String formatAntiCheatAnalysis(InterviewSession session) {
        int blur = session.getBlurCount() != null ? session.getBlurCount() : 0;
        int paste = session.getPasteCount() != null ? session.getPasteCount() : 0;
        return String.format("Blur count: %d, Paste count: %d, Reason: %s",
                blur, paste, session.getSubmissionReason() != null ? session.getSubmissionReason().name() : "MANUAL");
    }
}
