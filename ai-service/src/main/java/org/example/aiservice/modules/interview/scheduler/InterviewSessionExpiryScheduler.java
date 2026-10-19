package org.example.aiservice.modules.interview.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.modules.interview.entity.InterviewSchedule;
import org.example.aiservice.modules.interview.entity.InterviewSession;
import org.example.aiservice.modules.interview.enums.SessionStatus;
import org.example.aiservice.modules.interview.enums.SubmissionReason;
import org.example.aiservice.modules.interview.repository.InterviewScheduleRepository;
import org.example.aiservice.modules.interview.repository.InterviewSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trụ cột 5 - Scheduled Cronjob quét tự động dọn dẹp các phiên phỏng vấn:
 * 1. SCHEDULE_EXPIRED_CRON: Chuyển INVITED -> EXPIRED nếu quá 48h (expire_at < NOW).
 * 2. ABANDONED_CLEANUP_CRON: Chuyển IN_PROGRESS -> SUBMITTED (TIMEOUT) nếu bỏ thi quá 24h sau deadline.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewSessionExpiryScheduler {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewScheduleRepository scheduleRepository;

    /**
     * Chạy định kỳ mỗi 15 phút: Quét và cập nhật trạng thái hết hạn cho các phiên INVITED
     */
    @Scheduled(cron = "0 */15 * * * ?")
    @Transactional
    public void cleanupExpiredInvitedSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<InterviewSession> invitedSessions = sessionRepository.findByStatus(SessionStatus.INVITED);
        int expiredCount = 0;

        for (InterviewSession session : invitedSessions) {
            InterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId()).orElse(null);
            if (schedule != null && schedule.getExpireAt() != null && schedule.getExpireAt().isBefore(now)) {
                session.setStatus(SessionStatus.EXPIRED);
                sessionRepository.save(session);
                expiredCount++;
            }
        }

        if (expiredCount > 0) {
            log.info("[SCHEDULE_EXPIRED_CRON] Auto-expired {} invited interview sessions exceeding expireAt", expiredCount);
        }
    }

    /**
     * Chạy định kỳ mỗi 30 phút: Dọn dẹp các phiên thi IN_PROGRESS bị bỏ rơi quá hạn
     */
    @Scheduled(cron = "0 */30 * * * ?")
    @Transactional
    public void cleanupAbandonedInProgressSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<InterviewSession> inProgressSessions = sessionRepository.findByStatus(SessionStatus.IN_PROGRESS);
        int closedCount = 0;

        for (InterviewSession session : inProgressSessions) {
            // Nếu session_deadline đã qua hơn 1 giờ mà chưa nộp -> Auto-submit dạng TIMEOUT
            if (session.getSessionDeadline() != null && session.getSessionDeadline().plusHours(1).isBefore(now)) {
                session.setStatus(SessionStatus.SUBMITTED);
                session.setSubmittedAt(now);
                session.setSubmissionReason(SubmissionReason.TIMEOUT);
                sessionRepository.save(session);
                closedCount++;
            }
        }

        if (closedCount > 0) {
            log.info("[ABANDONED_CLEANUP_CRON] Auto-submitted {} abandoned in-progress sessions exceeding deadline", closedCount);
        }
    }
}
