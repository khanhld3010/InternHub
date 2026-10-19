package org.example.aiservice.modules.interview.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.modules.interview.entity.InterviewEvaluation;
import org.example.aiservice.modules.interview.enums.GradingStatus;
import org.example.aiservice.modules.interview.repository.InterviewEvaluationRepository;
import org.example.aiservice.modules.interview.service.AsyncInterviewEvaluationCoordinator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler tự động quét và kích hoạt chấm lại các bài thi bị kẹt ở trạng thái FAILED_RETRYABLE
 * Chạy định kỳ để tự động phục hồi sau khi hạn mức Quota của AI/Gemini được cấp lại.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewEvaluationRetryScheduler {

    private final InterviewEvaluationRepository evaluationRepository;
    private final AsyncInterviewEvaluationCoordinator evaluationCoordinator;

    /**
     * Chạy định kỳ mỗi 5 phút (delay 60 giây sau khởi động)
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void retryFailedEvaluations() {
        List<InterviewEvaluation> failedEvals = evaluationRepository.findByGradingStatusAndIsActiveTrue(GradingStatus.FAILED_RETRYABLE);
        if (failedEvals.isEmpty()) {
            return;
        }

        log.info("[InterviewEvaluationRetryScheduler] Phát hiện {} bài thi FAILED_RETRYABLE. Bắt đầu kích hoạt chấm lại tự động...", failedEvals.size());
        for (InterviewEvaluation eval : failedEvals) {
            try {
                evaluationCoordinator.coordinateEvaluationAsync(
                        eval.getSessionId(),
                        "RETRY_SCHEDULER",
                        "Tự động chấm lại sau khi khôi phục AI Quota"
                );
            } catch (Exception e) {
                log.error("[InterviewEvaluationRetryScheduler] Lỗi khi kích hoạt chấm lại cho sessionId={}: {}", eval.getSessionId(), e.getMessage());
            }
        }
    }
}
