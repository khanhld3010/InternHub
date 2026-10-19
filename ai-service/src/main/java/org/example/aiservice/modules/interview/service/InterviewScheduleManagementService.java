package org.example.aiservice.modules.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.modules.interview.dto.request.CreateInterviewScheduleRequest;
import org.example.aiservice.modules.interview.dto.response.InterviewScheduleDetailResponse;
import org.example.aiservice.modules.interview.entity.InterviewQuestionSet;
import org.example.aiservice.modules.interview.entity.InterviewSchedule;
import org.example.aiservice.modules.interview.entity.InterviewSession;
import org.example.aiservice.modules.interview.enums.SessionStatus;
import org.example.aiservice.client.InternServiceClient;
import org.example.aiservice.client.NotificationServiceClient;
import org.example.aiservice.client.dto.CreateNotificationInternalRequest;
import org.example.aiservice.client.dto.InternProfileClientDto;
import org.example.aiservice.dto.response.ApiResponse;
import org.example.aiservice.modules.interview.dto.response.InternInterviewItemResponse;
import org.example.aiservice.modules.interview.repository.InterviewQuestionRepository;
import org.example.aiservice.modules.interview.repository.InterviewQuestionSetRepository;
import org.example.aiservice.modules.interview.repository.InterviewScheduleRepository;
import org.example.aiservice.modules.interview.repository.InterviewSessionRepository;
import org.example.aiservice.modules.interview.util.TokenSecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewScheduleManagementService {

    private final InterviewScheduleRepository scheduleRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionSetRepository questionSetRepository;
    private final InterviewQuestionRepository questionRepository;
    private final NotificationServiceClient notificationServiceClient;
    private final InternServiceClient internServiceClient;
    private final CandidateAuthService candidateAuthService;

    @Value("${app.frontend.candidate-interview-url:http://localhost:5173/interview/verify}")
    private String candidateInterviewBaseUrl;

    @Value("${app.notification-service.internal-token:internhub-internal-secret-token-2026}")
    private String internalNotificationToken;

    /**
     * API 1: HR Tạo Lịch Phỏng Vấn Mới & Mời Ứng Viên
     */
    @Transactional
    public InterviewScheduleDetailResponse createSchedule(CreateInterviewScheduleRequest request, String hrUserId) {
        InterviewQuestionSet questionSet = questionSetRepository.findById(request.getQuestionSetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question set not found"));

        int expireHours = request.getExpireHours() != null ? request.getExpireHours() : 48;
        LocalDateTime expireAt = LocalDateTime.now().plusHours(expireHours);

        InterviewSchedule schedule = scheduleRepository.save(InterviewSchedule.builder()
                .title(request.getTitle())
                .questionSetId(questionSet.getId())
                .programId(request.getProgramId())
                .interviewMode(request.getInterviewMode())
                .durationPerQuestionSec(request.getDurationPerQuestionSec())
                .totalDurationMinutes(request.getTotalDurationMinutes())
                .allowRevisitQuestions(request.getAllowRevisitQuestions())
                .maxBlurStrikes(request.getMaxBlurStrikes())
                .autoSubmitOnExceed(request.getAutoSubmitOnExceed())
                .expireAt(expireAt)
                .createdByHrId(hrUserId)
                .build());

        List<InterviewScheduleDetailResponse.CandidateSessionSummary> sessionSummaries = new ArrayList<>();

        for (CreateInterviewScheduleRequest.CandidateInviteItem item : request.getCandidateList()) {
            // Sinh raw token 256-bit entropy, chỉ lưu hash SHA-256 vào DB
            String rawInviteToken = TokenSecurityUtil.generateRawInviteToken();
            String tokenHash = TokenSecurityUtil.hashToken(rawInviteToken);

            InterviewSession session = sessionRepository.save(InterviewSession.builder()
                    .scheduleId(schedule.getId())
                    .candidateId(item.getCandidateId())
                    .candidateEmail(item.getEmail())
                    .candidateName(item.getFullName())
                    .inviteTokenHash(tokenHash)
                    .status(SessionStatus.INVITED)
                    .build());

            // Link an toàn sử dụng URL fragment #token=... (không lộ access log/referer)
            String safeMagicLink = candidateInterviewBaseUrl + "#token=" + rawInviteToken;

            // TODO: Bắn event gửi email mời qua notification-service
            // Gửi thông báo WebSocket + lưu Notification DB cho Thực tập sinh nếu đã có tài khoản
            try {
                Long targetUserId = null;
                if (item.getCandidateId() != null) {
                    try {
                        ApiResponse<InternProfileClientDto> internRes = internServiceClient.getInternById(item.getCandidateId(), null);
                        if (internRes != null && internRes.getData() != null && internRes.getData().getUserId() != null) {
                            targetUserId = internRes.getData().getUserId();
                        }
                    } catch (Exception ex) {
                        log.debug("Could not resolve userId via internId {}: {}", item.getCandidateId(), ex.getMessage());
                    }
                }

                if (targetUserId != null) {
                    notificationServiceClient.sendNotification(
                            internalNotificationToken,
                            CreateNotificationInternalRequest.builder()
                                    .recipientId(targetUserId)
                                    .title("Lời mời phỏng vấn AI mới")
                                    .content(String.format("Bạn vừa nhận được lời mời tham gia buổi phỏng vấn AI: \"%s\". Vui lòng vào mục Lịch phỏng vấn để hoàn thành trước hạn.", schedule.getTitle()))
                                    .type("AI_INTERVIEW_INVITATION")
                                    .referenceType("AI_INTERVIEW")
                                    .referenceId(String.valueOf(session.getId()))
                                    .actionUrl("/intern/dashboard")
                                    .build()
                    );
                    log.info("Dispatched interview invitation notification to userId {}", targetUserId);
                }
            } catch (Exception notifEx) {
                log.warn("Failed to dispatch notification to candidate {}: {}", item.getEmail(), notifEx.getMessage());
            }

            sessionSummaries.add(InterviewScheduleDetailResponse.CandidateSessionSummary.builder()
                    .sessionId(session.getId())
                    .candidateId(session.getCandidateId())
                    .candidateEmail(session.getCandidateEmail())
                    .candidateName(session.getCandidateName())
                    .status(session.getStatus())
                    .blurCount(session.getBlurCount())
                    .pasteCount(session.getPasteCount())
                    .manualInviteLink(safeMagicLink)
                    .build());
        }

        return InterviewScheduleDetailResponse.builder()
                .scheduleId(schedule.getId())
                .title(schedule.getTitle())
                .questionSetId(questionSet.getId())
                .questionSetTitle(questionSet.getTitle())
                .programId(schedule.getProgramId())
                .interviewMode(schedule.getInterviewMode())
                .durationPerQuestionSec(schedule.getDurationPerQuestionSec())
                .totalDurationMinutes(schedule.getTotalDurationMinutes())
                .allowRevisitQuestions(schedule.getAllowRevisitQuestions())
                .maxBlurStrikes(schedule.getMaxBlurStrikes())
                .autoSubmitOnExceed(schedule.getAutoSubmitOnExceed())
                .expireAt(schedule.getExpireAt())
                .createdByHrId(schedule.getCreatedByHrId())
                .createdAt(schedule.getCreatedAt())
                .sessions(sessionSummaries)
                .build();
    }

    /**
     * API 2: Lấy chi tiết đợt phỏng vấn cho HR
     */
    @Transactional(readOnly = true)
    public InterviewScheduleDetailResponse getScheduleDetail(Long scheduleId) {
        InterviewSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        InterviewQuestionSet questionSet = questionSetRepository.findById(schedule.getQuestionSetId())
                .orElse(null);

        List<InterviewSession> sessions = sessionRepository.findByScheduleId(scheduleId);
        List<InterviewScheduleDetailResponse.CandidateSessionSummary> sessionSummaries = sessions.stream()
                .map(s -> InterviewScheduleDetailResponse.CandidateSessionSummary.builder()
                        .sessionId(s.getId())
                        .candidateId(s.getCandidateId())
                        .candidateEmail(s.getCandidateEmail())
                        .candidateName(s.getCandidateName())
                        .status(s.getStatus())
                        .blurCount(s.getBlurCount())
                        .pasteCount(s.getPasteCount())
                        .sessionStartedAt(s.getSessionStartedAt())
                        .submittedAt(s.getSubmittedAt())
                        .submissionReason(s.getSubmissionReason())
                        .build())
                .collect(Collectors.toList());

        return InterviewScheduleDetailResponse.builder()
                .scheduleId(schedule.getId())
                .title(schedule.getTitle())
                .questionSetId(schedule.getQuestionSetId())
                .questionSetTitle(questionSet != null ? questionSet.getTitle() : "Bộ câu hỏi")
                .programId(schedule.getProgramId())
                .interviewMode(schedule.getInterviewMode())
                .durationPerQuestionSec(schedule.getDurationPerQuestionSec())
                .totalDurationMinutes(schedule.getTotalDurationMinutes())
                .allowRevisitQuestions(schedule.getAllowRevisitQuestions())
                .maxBlurStrikes(schedule.getMaxBlurStrikes())
                .autoSubmitOnExceed(schedule.getAutoSubmitOnExceed())
                .expireAt(schedule.getExpireAt())
                .createdByHrId(schedule.getCreatedByHrId())
                .createdAt(schedule.getCreatedAt())
                .sessions(sessionSummaries)
                .build();
    }

    /**
     * API 3: Lấy danh sách lịch phỏng vấn theo Program ID (Hỗ trợ phân trang)
     */
    @Transactional(readOnly = true)
    public Page<InterviewSchedule> getSchedulesByProgram(Long programId, Pageable pageable) {
        if (programId != null) {
            return scheduleRepository.findByProgramId(programId, pageable);
        }
        return scheduleRepository.findAll(pageable);
    }

    /**
     * API 4: Hủy đợt phỏng vấn (CANCELLED)
     */
    @Transactional
    public void cancelSchedule(Long scheduleId) {
        InterviewSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        List<InterviewSession> sessions = sessionRepository.findByScheduleId(scheduleId);
        for (InterviewSession s : sessions) {
            if (s.getStatus() != SessionStatus.SUBMITTED) {
                s.setStatus(SessionStatus.CANCELLED);
                sessionRepository.save(s);
            }
        }
        log.info("Schedule {} cancelled and non-submitted sessions marked as CANCELLED", scheduleId);
    }

    /**
     * API 4.1: Cập nhật thông tin đợt phỏng vấn cho HR và đồng bộ sang ứng viên
     */
    @Transactional
    public InterviewScheduleDetailResponse updateSchedule(Long scheduleId, org.example.aiservice.modules.interview.dto.request.UpdateInterviewScheduleRequest request) {
        InterviewSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            schedule.setTitle(request.getTitle().trim());
        }
        if (request.getQuestionSetId() != null) {
            InterviewQuestionSet newSet = questionSetRepository.findById(request.getQuestionSetId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question set not found"));
            schedule.setQuestionSetId(newSet.getId());
        }
        if (request.getInterviewMode() != null) {
            schedule.setInterviewMode(request.getInterviewMode());
        }
        if (request.getDurationPerQuestionSec() != null) {
            schedule.setDurationPerQuestionSec(request.getDurationPerQuestionSec());
        }
        if (request.getTotalDurationMinutes() != null) {
            schedule.setTotalDurationMinutes(request.getTotalDurationMinutes());
        }
        if (request.getAllowRevisitQuestions() != null) {
            schedule.setAllowRevisitQuestions(request.getAllowRevisitQuestions());
        }
        if (request.getMaxBlurStrikes() != null) {
            schedule.setMaxBlurStrikes(request.getMaxBlurStrikes());
        }
        if (request.getAutoSubmitOnExceed() != null) {
            schedule.setAutoSubmitOnExceed(request.getAutoSubmitOnExceed());
        }
        if (request.getExpireHours() != null) {
            schedule.setExpireAt(LocalDateTime.now().plusHours(request.getExpireHours()));
        }

        schedule = scheduleRepository.save(schedule);
        log.info("Schedule {} successfully updated by HR", scheduleId);

        return getScheduleDetail(scheduleId);
    }

    /**
     * API 5: Gửi lại email lời mời cho Ứng viên (kèm sinh mới Magic Link an toàn)
     */
    @Transactional
    public String resendInvite(Long scheduleId, Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getStatus() == SessionStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session already submitted");
        }

        // Sinh mới raw token và hash lại để làm mới phiên mời
        String newRawToken = TokenSecurityUtil.generateRawInviteToken();
        session.setInviteTokenHash(TokenSecurityUtil.hashToken(newRawToken));
        session.setStatus(SessionStatus.INVITED);
        sessionRepository.save(session);

        String safeMagicLink = candidateInterviewBaseUrl + "#token=" + newRawToken;
        log.info("Resent invite for candidate {}. New link: {}", session.getCandidateEmail(), safeMagicLink);

        // TODO: Gửi email qua notification-service
        return safeMagicLink;
    }

    /**
     * API 6: Thu hồi phiên phỏng vấn cá nhân (Revoke Session)
     */
    @Transactional
    public void revokeSession(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        session.setStatus(SessionStatus.CANCELLED);
        sessionRepository.save(session);
        log.info("Session {} revoked by HR", sessionId);
    }

    /**
     * API 7: Lấy danh sách các buổi phỏng vấn AI của Thực tập sinh đang đăng nhập
     */
    @Transactional(readOnly = true)
    public List<InternInterviewItemResponse> getMyInterviews(String usernameOrEmail, String authHeader) {
        log.info("Fetching interviews for candidate/intern user: {}", usernameOrEmail);

        // 1. Tìm các sessions theo username hoặc email trực tiếp
        List<InterviewSession> sessions = sessionRepository.findByCandidateEmailOrderByCreatedAtDesc(usernameOrEmail);

        // 2. Nếu chưa thấy và có authHeader, gọi intern-service lấy hồ sơ TTS (chứa email thực tế và candidateId)
        if (sessions.isEmpty() && authHeader != null && !authHeader.isBlank()) {
            try {
                ApiResponse<InternProfileClientDto> myProfileRes = internServiceClient.getMyProfile(authHeader);
                if (myProfileRes != null && myProfileRes.getData() != null) {
                    InternProfileClientDto profile = myProfileRes.getData();
                    Long candidateId = profile.getId();
                    String candidateEmail = profile.getEmail();
                    log.info("Resolved intern profile: id={}, email={}", candidateId, candidateEmail);

                    sessions = sessionRepository.findByCandidateIdOrEmail(candidateId, candidateEmail);
                }
            } catch (Exception ex) {
                log.warn("Failed to fetch my-profile from intern-service: {}", ex.getMessage());
            }
        }

        // 3. Fallback thêm bằng query findByCandidateIdOrEmail nếu vẫn rỗng
        if (sessions.isEmpty()) {
            try {
                sessions = sessionRepository.findByCandidateIdOrEmail(null, usernameOrEmail);
            } catch (Exception ex) {
                log.debug("Fallback query failed: {}", ex.getMessage());
            }
        }

        List<InternInterviewItemResponse> results = new ArrayList<>();

        for (InterviewSession s : sessions) {
            InterviewSchedule schedule = scheduleRepository.findById(s.getScheduleId()).orElse(null);
            if (schedule == null) continue;

            // Kiểm tra trạng thái hết hạn
            SessionStatus currentStatus = s.getStatus();
            if (currentStatus == SessionStatus.INVITED && schedule.getExpireAt() != null && schedule.getExpireAt().isBefore(LocalDateTime.now())) {
                currentStatus = SessionStatus.EXPIRED;
            }

            // Sinh direct session token cho phép vào buồng thi trực tiếp mà không cần dán token thô
            String directJwt = null;
            if (currentStatus == SessionStatus.INVITED || currentStatus == SessionStatus.IN_PROGRESS) {
                directJwt = candidateAuthService.generateCandidateSessionJwt(s);
            }

            int questionCount = questionRepository.findByQuestionSetIdOrderByDisplayOrderAsc(schedule.getQuestionSetId()).size();

            results.add(InternInterviewItemResponse.builder()
                    .sessionId(s.getId())
                    .scheduleId(schedule.getId())
                    .interviewTitle(schedule.getTitle())
                    .status(currentStatus)
                    .interviewMode(schedule.getInterviewMode())
                    .totalQuestions(questionCount)
                    .durationPerQuestionSec(schedule.getDurationPerQuestionSec())
                    .totalDurationMinutes(schedule.getTotalDurationMinutes())
                    .expireAt(schedule.getExpireAt())
                    .startedAt(s.getSessionStartedAt())
                    .submittedAt(s.getSubmittedAt())
                    .directRoomToken(directJwt)
                    .build());
        }

        return results;
    }

    /**
     * Tra cứu phiên phỏng vấn gần nhất của ứng viên
     */
    @Transactional(readOnly = true)
    public Optional<InterviewSession> findLatestSessionForCandidate(Long candidateId, String email) {
        if (candidateId != null && email != null && !email.trim().isEmpty()) {
            List<InterviewSession> sessions = sessionRepository.findByCandidateIdOrEmail(candidateId, email.trim());
            if (!sessions.isEmpty()) {
                return Optional.of(sessions.get(0));
            }
        } else if (candidateId != null) {
            List<InterviewSession> sessions = sessionRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
            if (!sessions.isEmpty()) {
                return Optional.of(sessions.get(0));
            }
        } else if (email != null && !email.trim().isEmpty()) {
            List<InterviewSession> sessions = sessionRepository.findByCandidateEmailOrderByCreatedAtDesc(email.trim());
            if (!sessions.isEmpty()) {
                return Optional.of(sessions.get(0));
            }
        }
        return Optional.empty();
    }
}
