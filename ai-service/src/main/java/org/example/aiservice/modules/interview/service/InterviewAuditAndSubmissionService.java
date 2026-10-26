package org.example.aiservice.modules.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.modules.interview.dto.request.CandidateEvidenceRequest;
import org.example.aiservice.modules.interview.dto.response.CandidateEvidenceResponse;
import org.example.aiservice.modules.interview.dto.response.ProctoringAlertEvent;
import org.example.aiservice.modules.interview.dto.response.SubmitInterviewSessionResponse;
import org.example.aiservice.modules.interview.entity.InterviewAuditLog;
import org.example.aiservice.modules.interview.entity.InterviewSchedule;
import org.example.aiservice.modules.interview.entity.InterviewSession;
import org.example.aiservice.modules.interview.enums.AuditEventType;
import org.example.aiservice.modules.interview.enums.ProctoringSeverity;
import org.example.aiservice.modules.interview.enums.SessionStatus;
import org.example.aiservice.modules.interview.enums.SubmissionReason;
import org.example.aiservice.modules.interview.repository.InterviewAuditLogRepository;
import org.example.aiservice.modules.interview.repository.InterviewScheduleRepository;
import org.example.aiservice.modules.interview.repository.InterviewSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewAuditAndSubmissionService {

    private static final long DEBOUNCE_WINDOW_MILLIS = 3000; // 3 giây debounce cho sự kiện Tab Blur

    private final InterviewSessionRepository sessionRepository;
    private final InterviewScheduleRepository scheduleRepository;
    private final InterviewAuditLogRepository auditLogRepository;
    private final CandidateSessionExecutionService candidateSessionExecutionService;
    private final AsyncInterviewEvaluationCoordinator evaluationCoordinator;
    private final ProctoringEventPublisher proctoringEventPublisher;

    // Lưu timestamp của lần blur gần nhất theo sessionId để debounce
    private final Map<Long, Long> lastBlurTimestampMap = new ConcurrentHashMap<>();

    /**
     * Ghi nhận bằng chứng Telemetry giám sát phòng thi với Debounce Window 3 giây
     */
    @Transactional
    public CandidateEvidenceResponse recordEvidence(Long sessionId,
                                                    CandidateEvidenceRequest request,
                                                    String authenticatedUserId) {
        InterviewSession session = candidateSessionExecutionService.validateCandidateAccess(sessionId, authenticatedUserId);

        if (session.getStatus() == SessionStatus.SUBMITTED) {
            return CandidateEvidenceResponse.builder()
                    .recorded(false)
                    .currentBlurCount(session.getBlurCount())
                    .maxStrikes(3)
                    .isTerminated(true)
                    .warningMessage("Phiên làm bài đã kết thúc.")
                    .build();
        }

        InterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        LocalDateTime now = LocalDateTime.now();
        long nowMillis = System.currentTimeMillis();
        boolean countIncremented = false;

        // Xử lý Debounce cho TAB_BLUR
        if (request.getEventType() == AuditEventType.TAB_BLUR) {
            Long lastBlur = lastBlurTimestampMap.get(sessionId);
            if (lastBlur == null || (nowMillis - lastBlur) > DEBOUNCE_WINDOW_MILLIS) {
                lastBlurTimestampMap.put(sessionId, nowMillis);
                session.setBlurCount(session.getBlurCount() + 1);
                countIncremented = true;
                log.info("Tab blur recorded for session {}. Current strikes: {}", sessionId, session.getBlurCount());
            } else {
                log.debug("Debounced tab blur event for session {}", sessionId);
            }
        } else if (request.getEventType() == AuditEventType.PASTE_DETECTED) {
            session.setPasteCount(session.getPasteCount() + 1);
            countIncremented = true;
        }

        // Lưu Audit Log Telemetry
        auditLogRepository.save(InterviewAuditLog.builder()
                .sessionId(sessionId)
                .eventType(request.getEventType())
                .eventMetadata(request.getMetadata())
                .clientTimestamp(request.getClientTimestamp() != null ? request.getClientTimestamp() : now)
                .build());

        int maxStrikes = schedule.getMaxBlurStrikes() != null ? schedule.getMaxBlurStrikes() : 3;
        boolean isTerminated = false;
        String warningMsg = null;
        ProctoringSeverity severity = ProctoringSeverity.INFO;

        // Kiểm tra ngưỡng vi phạm (Level 2 + Threshold Strike)
        if (session.getBlurCount() >= maxStrikes) {
            severity = ProctoringSeverity.DISQUALIFIED;
            if (Boolean.TRUE.equals(schedule.getAutoSubmitOnExceed())) {
                log.warn("Session {} exceeded max blur strikes ({}). Auto-submitting session.", sessionId, maxStrikes);
                session.setStatus(SessionStatus.SUBMITTED);
                session.setSubmittedAt(now);
                session.setSubmissionReason(SubmissionReason.STRIKE_LIMIT_EXCEEDED);
                isTerminated = true;
                warningMsg = "Bạn đã vượt quá số lần rời khỏi màn hình cho phép (" + maxStrikes + " lần). Bài thi đã tự động nộp.";
            } else {
                warningMsg = "CẢNH BÁO: Bạn đã đạt ngưỡng vi phạm (" + session.getBlurCount() + "/" + maxStrikes + "). Hành vi này được lưu vào biên bản.";
            }
        } else if (countIncremented && request.getEventType() == AuditEventType.TAB_BLUR) {
            severity = (session.getBlurCount() >= 2) ? ProctoringSeverity.CRITICAL : ProctoringSeverity.WARNING;
            warningMsg = "Cảnh báo: Bạn vừa rời khỏi màn hình làm bài! Lần vi phạm: " + session.getBlurCount() + "/" + maxStrikes + ".";
        }

        if (countIncremented) {
            proctoringEventPublisher.publishProctoringAlert(ProctoringAlertEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .scheduleId(session.getScheduleId())
                    .candidateId(session.getCandidateId())
                    .candidateName(session.getCandidateName())
                    .candidateEmail(session.getCandidateEmail())
                    .eventType(request.getEventType())
                    .severity(severity)
                    .strikeCount(session.getBlurCount())
                    .maxStrikes(maxStrikes)
                    .details(request.getMetadata() != null ? request.getMetadata() : warningMsg)
                    .timestamp(Instant.now())
                    .build());
        }

        session.setLastActiveAt(now);
        sessionRepository.save(session);

        return CandidateEvidenceResponse.builder()
                .recorded(countIncremented)
                .currentBlurCount(session.getBlurCount())
                .maxStrikes(maxStrikes)
                .isTerminated(isTerminated)
                .warningMessage(warningMsg)
                .build();
    }

    /**
     * Nộp bài phỏng vấn với Atomic Compare-And-Swap (CAS) và Idempotency
     */
    @Transactional
    public SubmitInterviewSessionResponse submitSession(Long sessionId,
                                                        SubmissionReason reason,
                                                        String authenticatedUserId) {
        InterviewSession session = candidateSessionExecutionService.validateCandidateAccess(sessionId, authenticatedUserId);

        // Nếu đã nộp trước đó -> Idempotent response
        if (session.getStatus() == SessionStatus.SUBMITTED) {
            return SubmitInterviewSessionResponse.builder()
                    .sessionId(sessionId)
                    .status(SessionStatus.SUBMITTED)
                    .submittedAt(session.getSubmittedAt())
                    .submissionReason(session.getSubmissionReason())
                    .message("Bài phỏng vấn đã được nộp trước đó.")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        SubmissionReason finalReason = (reason != null) ? reason : SubmissionReason.MANUAL;

        // Thực thi Atomic CAS UPDATE
        int rowsUpdated = sessionRepository.updateStatusAtomic(
                sessionId,
                SessionStatus.IN_PROGRESS,
                SessionStatus.SUBMITTED,
                now,
                finalReason
        );

        if (rowsUpdated == 0) {
            // Trường hợp phiên vừa được update bởi luồng khác (ví dụ timeout submit đồng thời)
            InterviewSession updatedSession = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
            return SubmitInterviewSessionResponse.builder()
                    .sessionId(sessionId)
                    .status(updatedSession.getStatus())
                    .submittedAt(updatedSession.getSubmittedAt())
                    .submissionReason(updatedSession.getSubmissionReason())
                    .message("Nộp bài thi thành công.")
                    .build();
        }

        // Dọn dẹp cache debounce trong bộ nhớ tránh memory leak
        lastBlurTimestampMap.remove(sessionId);

        log.info("Session {} successfully submitted with reason: {}. Kích hoạt AI Interview Grader ngầm.", sessionId, finalReason);

        // Kích hoạt chấm điểm bất đồng bộ qua AI Grader Pipeline (Task 4)
        evaluationCoordinator.coordinateEvaluationAsync(sessionId, "SUBMISSION_EVENT", "Tự động chấm điểm khi nộp bài");

        return SubmitInterviewSessionResponse.builder()
                .sessionId(sessionId)
                .status(SessionStatus.SUBMITTED)
                .submittedAt(now)
                .submissionReason(finalReason)
                .message("Nộp bài phỏng vấn thành công. Cảm ơn bạn đã tham gia!")
                .build();
    }

    /**
     * Giám thị (HR) thực hiện đình chỉ thi thủ công hoặc thu bài trực tiếp
     */
    @Transactional
    public void disqualifySession(Long sessionId, String reason, String actorUserId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getStatus() == SessionStatus.SUBMITTED) {
            log.warn("Session {} is already submitted, skip manual disqualification", sessionId);
            return;
        }

        InterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        int maxStrikes = schedule.getMaxBlurStrikes() != null ? schedule.getMaxBlurStrikes() : 3;
        LocalDateTime now = LocalDateTime.now();

        session.setStatus(SessionStatus.SUBMITTED);
        session.setSubmittedAt(now);
        session.setSubmissionReason(SubmissionReason.STRIKE_LIMIT_EXCEEDED);
        session.setLastActiveAt(now);
        sessionRepository.save(session);

        // Lưu Audit Log
        auditLogRepository.save(InterviewAuditLog.builder()
                .sessionId(sessionId)
                .eventType(AuditEventType.TAB_BLUR)
                .eventMetadata("DISQUALIFIED_BY_HR: " + reason + " (Actor: " + actorUserId + ")")
                .clientTimestamp(now)
                .build());

        // Publish event lên Redis
        proctoringEventPublisher.publishProctoringAlert(ProctoringAlertEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .scheduleId(session.getScheduleId())
                .candidateId(session.getCandidateId())
                .candidateName(session.getCandidateName())
                .candidateEmail(session.getCandidateEmail())
                .eventType(AuditEventType.TAB_BLUR)
                .severity(ProctoringSeverity.DISQUALIFIED)
                .strikeCount(session.getBlurCount())
                .maxStrikes(maxStrikes)
                .details(reason != null ? reason : "Đình chỉ bởi giám thị phòng thi")
                .timestamp(Instant.now())
                .build());

        log.info("Session {} successfully disqualified by HR {}", sessionId, actorUserId);
    }
}
