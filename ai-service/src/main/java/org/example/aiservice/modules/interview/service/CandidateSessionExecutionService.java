package org.example.aiservice.modules.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.modules.interview.dto.request.SaveCandidateAnswerRequest;
import org.example.aiservice.modules.interview.dto.response.CandidateQuestionResponse;
import org.example.aiservice.modules.interview.dto.response.CandidateRecoveryStateResponse;
import org.example.aiservice.modules.interview.dto.response.SaveCandidateAnswerResponse;
import org.example.aiservice.modules.interview.entity.InterviewAnswer;
import org.example.aiservice.modules.interview.entity.InterviewQuestion;
import org.example.aiservice.modules.interview.entity.InterviewSchedule;
import org.example.aiservice.modules.interview.entity.InterviewSession;
import org.example.aiservice.modules.interview.enums.InterviewMode;
import org.example.aiservice.modules.interview.enums.SessionStatus;
import org.example.aiservice.modules.interview.repository.InterviewAnswerRepository;
import org.example.aiservice.modules.interview.repository.InterviewQuestionRepository;
import org.example.aiservice.modules.interview.repository.InterviewScheduleRepository;
import org.example.aiservice.modules.interview.repository.InterviewSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateSessionExecutionService {

    private static final int GRACE_PERIOD_SECONDS = 5; // Dung sai độ trễ mạng 5 giây

    private final InterviewSessionRepository sessionRepository;
    private final InterviewScheduleRepository scheduleRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;

    /**
     * Xác thực quyền truy cập của Candidate Session Token với Session ID yêu cầu
     */
    public InterviewSession validateCandidateAccess(Long sessionId, String authenticatedUserId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview session not found"));

        // Cho phép 1: Token buồng thi của ứng viên (candidate-{sessionId})
        boolean isCandidateSessionMatch = ("candidate-" + sessionId).equals(authenticatedUserId);

        // Cho phép 2: Người dùng đăng nhập có email hoặc candidateId trùng khớp với buổi thi
        boolean isOwnerIntern = authenticatedUserId != null && (
                authenticatedUserId.equalsIgnoreCase(session.getCandidateEmail()) ||
                (session.getCandidateId() != null && String.valueOf(session.getCandidateId()).equals(authenticatedUserId))
        );

        // Cho phép 3: HR hoặc Admin
        boolean isPrivileged = "hr".equalsIgnoreCase(authenticatedUserId) || "admin".equalsIgnoreCase(authenticatedUserId);

        if (!isCandidateSessionMatch && !isOwnerIntern && !isPrivileged) {
            log.warn("Access denied for user {} accessing session {}. Expected candidate-{} or owner {}",
                    authenticatedUserId, sessionId, sessionId, session.getCandidateEmail());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Candidate session mismatch");
        }

        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Interview session has been cancelled by HR");
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Interview session has expired");
        }

        return session;
    }

    /**
     * API 1: Bắt đầu làm bài thi (Start Session)
     */
    @Transactional
    public CandidateQuestionResponse startSession(Long sessionId, String authenticatedUserId) {
        InterviewSession session = validateCandidateAccess(sessionId, authenticatedUserId);
        InterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        LocalDateTime now = LocalDateTime.now();

        List<InterviewQuestion> questions = questionRepository
                .findByQuestionSetIdOrderByDisplayOrderAsc(schedule.getQuestionSetId());
        if (questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interview question set has no questions");
        }

        InterviewQuestion firstQuestion = questions.get(0);

        if (session.getStatus() == SessionStatus.INVITED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            session.setSessionStartedAt(now);

            // Tính Server-Authoritative Deadlines
            if (schedule.getInterviewMode() == InterviewMode.OVERALL_ASSESSMENT) {
                int totalMinutes = schedule.getTotalDurationMinutes() != null ? schedule.getTotalDurationMinutes() : 30;
                session.setSessionDeadline(now.plusMinutes(totalMinutes));
            }

            int perQuestionSec = schedule.getDurationPerQuestionSec() != null ? schedule.getDurationPerQuestionSec() : 180;
            session.setCurrentQuestionId(firstQuestion.getId());
            session.setCurrentQuestionDeadline(now.plusSeconds(perQuestionSec));
            session.setLastActiveAt(now);

            sessionRepository.save(session);
        }

        // Tạo sẵn bản ghi câu trả lời nháp ban đầu nếu chưa có
        answerRepository.findBySessionIdAndQuestionId(sessionId, firstQuestion.getId())
                .orElseGet(() -> answerRepository.save(InterviewAnswer.builder()
                        .sessionId(sessionId)
                        .questionId(firstQuestion.getId())
                        .answerText("")
                        .revision(1)
                        .isSubmitted(false)
                        .timeSpentSeconds(0)
                        .serverReceivedAt(now)
                        .build()));

        LocalDateTime deadline = schedule.getInterviewMode() == InterviewMode.OVERALL_ASSESSMENT
                ? session.getSessionDeadline()
                : session.getCurrentQuestionDeadline();

        long remainingSec = Math.max(0, Duration.between(now, deadline).getSeconds());

        return CandidateQuestionResponse.builder()
                .questionId(firstQuestion.getId())
                .displayOrder(firstQuestion.getDisplayOrder())
                .category(firstQuestion.getCategory())
                .difficulty(firstQuestion.getDifficulty())
                .questionText(firstQuestion.getQuestionText())
                .focusArea(firstQuestion.getFocusArea())
                .suggestedScoreWeight(firstQuestion.getSuggestedScoreWeight())
                .serverDeadline(deadline)
                .remainingSeconds(remainingSec)
                .savedAnswerText("")
                .currentRevision(1)
                .isSubmitted(false)
                .questionStatus("ACTIVE")
                .build();
    }

    /**
     * API 2: Lưu nháp hoặc nộp câu hỏi với Optimistic Locking & Grace Period
     */
    @Transactional
    public SaveCandidateAnswerResponse saveAnswer(Long sessionId, Long questionId,
                                                  SaveCandidateAnswerRequest request,
                                                  String authenticatedUserId) {
        InterviewSession session = validateCandidateAccess(sessionId, authenticatedUserId);
        if (session.getStatus() == SessionStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Interview has already been submitted");
        }

        InterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveDeadline = schedule.getInterviewMode() == InterviewMode.OVERALL_ASSESSMENT
                ? session.getSessionDeadline()
                : session.getCurrentQuestionDeadline();

        // 1. Kiểm tra Server Authoritative Deadline kèm 5 giây Grace Period
        if (effectiveDeadline != null && now.isAfter(effectiveDeadline.plusSeconds(GRACE_PERIOD_SECONDS))) {
            log.warn("Submission rejected due to timeout. Now: {}, Deadline: {}", now, effectiveDeadline);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "QUESTION_TIME_EXPIRED: Server deadline has passed");
        }

        // 2. Tìm hoặc khởi tạo bản ghi câu trả lời
        InterviewAnswer answer = answerRepository.findBySessionIdAndQuestionId(sessionId, questionId)
                .orElseGet(() -> InterviewAnswer.builder()
                        .sessionId(sessionId)
                        .questionId(questionId)
                        .answerText("")
                        .revision(0)
                        .isSubmitted(false)
                        .timeSpentSeconds(0)
                        .serverReceivedAt(now)
                        .build());

        if (Boolean.TRUE.equals(answer.getIsSubmitted())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This question has already been submitted and cannot be modified");
        }

        // 3. Kiểm tra Optimistic Locking Revision Control
        if (answer.getId() != null && !Objects.equals(answer.getRevision(), request.getBaseRevision())) {
            log.warn("Revision conflict for session {} question {}: DB={}, Request={}",
                    sessionId, questionId, answer.getRevision(), request.getBaseRevision());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "REVISION_CONFLICT: Draft was updated from another request. Base revision mismatch.");
        }

        // 4. Cập nhật câu trả lời an toàn
        answer.setAnswerText(request.getAnswerText());
        answer.setInputMethod(request.getInputMethod() != null ? request.getInputMethod() : org.example.aiservice.modules.interview.enums.InputMethod.TYPING);
        answer.setRevision(answer.getRevision() + 1);
        answer.setTimeSpentSeconds(request.getTimeSpentSeconds() != null ? request.getTimeSpentSeconds() : 0);
        answer.setIsSubmitted(Boolean.TRUE.equals(request.getIsSubmitted()));
        answer.setServerReceivedAt(now);

        InterviewAnswer saved = answerRepository.save(answer);
        session.setLastActiveAt(now);

        // Nếu ứng viên chốt nộp câu hỏi hiện tại trong chế độ PER_QUESTION, chuyển sang câu tiếp theo và cấp mới Server Deadline
        LocalDateTime nextQuestionDeadline = effectiveDeadline;
        if (Boolean.TRUE.equals(request.getIsSubmitted()) && schedule.getInterviewMode() == InterviewMode.PER_QUESTION) {
            List<InterviewQuestion> allQuestions = questionRepository
                    .findByQuestionSetIdOrderByDisplayOrderAsc(schedule.getQuestionSetId());

            InterviewQuestion nextQuestion = null;
            for (int i = 0; i < allQuestions.size(); i++) {
                if (Objects.equals(allQuestions.get(i).getId(), questionId)) {
                    if (i + 1 < allQuestions.size()) {
                        nextQuestion = allQuestions.get(i + 1);
                    }
                    break;
                }
            }

            if (nextQuestion != null) {
                int durationSec = schedule.getDurationPerQuestionSec() != null ? schedule.getDurationPerQuestionSec() : 180;
                nextQuestionDeadline = now.plusSeconds(durationSec);
                session.setCurrentQuestionId(nextQuestion.getId());
                session.setCurrentQuestionDeadline(nextQuestionDeadline);
                log.info("Advanced session {} to next question {} with fresh deadline {}",
                        sessionId, nextQuestion.getId(), nextQuestionDeadline);
            }
        }

        sessionRepository.save(session);

        return SaveCandidateAnswerResponse.builder()
                .questionId(questionId)
                .newRevision(saved.getRevision())
                .serverDeadline(nextQuestionDeadline)
                .isSubmitted(saved.getIsSubmitted())
                .synced(true)
                .message("Answer saved successfully")
                .build();
    }

    /**
     * API 3: Khôi phục trạng thái buồng thi khi F5 Reload hoặc mở lại máy
     */
    @Transactional(readOnly = true)
    public CandidateRecoveryStateResponse getRecoveryState(Long sessionId, String authenticatedUserId) {
        InterviewSession session = validateCandidateAccess(sessionId, authenticatedUserId);
        InterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        LocalDateTime now = LocalDateTime.now();

        List<InterviewQuestion> allQuestions = questionRepository
                .findByQuestionSetIdOrderByDisplayOrderAsc(schedule.getQuestionSetId());

        Map<Long, InterviewAnswer> answerMap = answerRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(InterviewAnswer::getQuestionId, Function.identity(), (a, b) -> a));

        List<CandidateQuestionResponse> questionResponses = new ArrayList<>();
        int activeIndex = 0;

        for (int i = 0; i < allQuestions.size(); i++) {
            InterviewQuestion q = allQuestions.get(i);
            InterviewAnswer ans = answerMap.get(q.getId());

            String qStatus = "PENDING";
            boolean isSub = ans != null && Boolean.TRUE.equals(ans.getIsSubmitted());

            if (isSub) {
                qStatus = "COMPLETED";
            } else if (Objects.equals(session.getCurrentQuestionId(), q.getId())) {
                qStatus = "ACTIVE";
                activeIndex = i;
            }

            LocalDateTime deadline = schedule.getInterviewMode() == InterviewMode.OVERALL_ASSESSMENT
                    ? session.getSessionDeadline()
                    : (Objects.equals(session.getCurrentQuestionId(), q.getId()) ? session.getCurrentQuestionDeadline() : null);

            long remSec = (deadline != null) ? Math.max(0, Duration.between(now, deadline).getSeconds()) : 0;

            questionResponses.add(CandidateQuestionResponse.builder()
                    .questionId(q.getId())
                    .displayOrder(q.getDisplayOrder())
                    .category(q.getCategory())
                    .difficulty(q.getDifficulty())
                    .questionText(q.getQuestionText())
                    .focusArea(q.getFocusArea())
                    .suggestedScoreWeight(q.getSuggestedScoreWeight())
                    .serverDeadline(deadline)
                    .remainingSeconds(remSec)
                    .savedAnswerText(ans != null ? ans.getAnswerText() : "")
                    .currentRevision(ans != null ? ans.getRevision() : 1)
                    .isSubmitted(isSub)
                    .questionStatus(qStatus)
                    .build());
        }

        long overallRem = session.getSessionDeadline() != null
                ? Math.max(0, Duration.between(now, session.getSessionDeadline()).getSeconds())
                : 0;

        return CandidateRecoveryStateResponse.builder()
                .sessionId(sessionId)
                .interviewTitle(schedule.getTitle())
                .candidateName(session.getCandidateName())
                .candidateEmail(session.getCandidateEmail())
                .status(session.getStatus())
                .interviewMode(schedule.getInterviewMode())
                .totalQuestions(allQuestions.size())
                .currentQuestionIndex(activeIndex)
                .serverNow(now)
                .sessionDeadline(session.getSessionDeadline())
                .remainingSecondsOverall(overallRem)
                .blurCount(session.getBlurCount())
                .maxBlurStrikes(schedule.getMaxBlurStrikes())
                .allowRevisitQuestions(schedule.getAllowRevisitQuestions())
                .questions(questionResponses)
                .build();
    }
}
