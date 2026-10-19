package org.example.aiservice.modules.interview.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.modules.interview.dto.request.ExchangeTokenRequest;
import org.example.aiservice.modules.interview.dto.response.CandidateSessionAccessResponse;
import org.example.aiservice.modules.interview.entity.InterviewSchedule;
import org.example.aiservice.modules.interview.entity.InterviewSession;
import org.example.aiservice.modules.interview.enums.SessionStatus;
import org.example.aiservice.modules.interview.repository.InterviewQuestionRepository;
import org.example.aiservice.modules.interview.repository.InterviewScheduleRepository;
import org.example.aiservice.modules.interview.repository.InterviewSessionRepository;
import org.example.aiservice.modules.interview.util.TokenSecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateAuthService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewScheduleRepository scheduleRepository;
    private final InterviewQuestionRepository questionRepository;

    @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String jwtSecret;

    // Rate Limiting theo IP: Tối đa 5 requests / phút / IP
    private final Map<String, RateLimitBucket> rateLimitMap = new ConcurrentHashMap<>();

    private static class RateLimitBucket {
        long lastResetTime = System.currentTimeMillis();
        int requestCount = 0;
    }

    public void checkRateLimit(String clientIp) {
        long now = System.currentTimeMillis();
        RateLimitBucket bucket = rateLimitMap.computeIfAbsent(clientIp, k -> new RateLimitBucket());
        synchronized (bucket) {
            if (now - bucket.lastResetTime > 60_000) {
                bucket.lastResetTime = now;
                bucket.requestCount = 0;
            }
            bucket.requestCount++;
            if (bucket.requestCount > 5) {
                log.warn("Rate limit exceeded for IP: {}", clientIp);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many token exchange attempts. Please try again later.");
            }
        }
    }

    @Transactional
    public CandidateSessionAccessResponse exchangeToken(ExchangeTokenRequest request, String clientIp) {
        checkRateLimit(clientIp);

        String hash = TokenSecurityUtil.hashToken(request.getRawInviteToken());
        InterviewSession session = sessionRepository.findByInviteTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired interview token"));

        InterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview schedule not found"));

        // Kiểm tra hạn chót truy cập
        if (schedule.getExpireAt() != null && schedule.getExpireAt().isBefore(LocalDateTime.now())) {
            session.setStatus(SessionStatus.EXPIRED);
            sessionRepository.save(session);
            throw new ResponseStatusException(HttpStatus.GONE, "Interview invitation has expired");
        }

        // Kiểm tra trạng thái phiên
        if (session.getStatus() == SessionStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Interview has already been submitted");
        }
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Interview session has been cancelled by HR");
        }
        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Interview session has expired");
        }

        // Sinh Candidate Session JWT (Hiệu lực 2 giờ)
        String sessionJwt = generateCandidateSessionJwt(session);

        // Đếm số câu hỏi của bộ đề
        int totalQuestions = questionRepository.findByQuestionSetIdOrderByDisplayOrderAsc(schedule.getQuestionSetId()).size();

        return CandidateSessionAccessResponse.builder()
                .candidateSessionToken(sessionJwt)
                .sessionId(session.getId())
                .candidateName(session.getCandidateName())
                .candidateEmail(session.getCandidateEmail())
                .interviewTitle(schedule.getTitle())
                .status(session.getStatus())
                .interviewMode(schedule.getInterviewMode())
                .totalQuestions(totalQuestions)
                .durationPerQuestionSec(schedule.getDurationPerQuestionSec())
                .totalDurationMinutes(schedule.getTotalDurationMinutes())
                .maxBlurStrikes(schedule.getMaxBlurStrikes())
                .allowRevisitQuestions(schedule.getAllowRevisitQuestions())
                .autoSubmitOnExceed(schedule.getAutoSubmitOnExceed())
                .build();
    }

    public String generateCandidateSessionJwt(InterviewSession session) {
        SecretKey key = getSigningKey(jwtSecret);
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 2 * 3600 * 1000); // 2 hours

        return Jwts.builder()
                .subject(session.getCandidateEmail())
                .claim("userId", "candidate-" + session.getId())
                .claim("sessionId", session.getId())
                .claim("candidateId", session.getCandidateId())
                .claim("roles", List.of("ROLE_CANDIDATE_INTERVIEW"))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    private SecretKey getSigningKey(String secret) {
        byte[] keyBytes;
        try {
            if (secret.matches("^[0-9a-fA-F]+$") && secret.length() >= 64) {
                keyBytes = HexFormat.of().parseHex(secret);
            } else {
                keyBytes = Decoders.BASE64.decode(secret);
            }
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
