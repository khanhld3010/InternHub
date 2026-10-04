package org.example.internservice.attendance.service;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.BadRequestException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class CheckInQrTokenManager {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int TTL_SECONDS = 60;

    @Getter
    @Builder
    public static class CheckInSession {
        private String token;
        private Long internId;
        private Long userId;
        private LocalDate workDate;
        private Double latitude;
        private Double longitude;
        private Double distance;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private volatile boolean consumed;

        public boolean isExpired(LocalDateTime now) {
            return now.isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, CheckInSession> sessionCache = new ConcurrentHashMap<>();

    public CheckInSession createSession(Long internId, Long userId, LocalDate workDate, Double latitude, Double longitude, Double distance) {
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        LocalDateTime expiresAt = now.plusSeconds(TTL_SECONDS);
        String token = UUID.randomUUID().toString();

        CheckInSession session = CheckInSession.builder()
                .token(token)
                .internId(internId)
                .userId(userId)
                .workDate(workDate)
                .latitude(latitude)
                .longitude(longitude)
                .distance(distance)
                .createdAt(now)
                .expiresAt(expiresAt)
                .consumed(false)
                .build();

        sessionCache.put(token, session);
        log.info("Đã tạo phiên QR Check-in [token: {}] cho Intern ID {} (hết hạn lúc {})",
                token, internId, expiresAt);
        return session;
    }

    public CheckInSession validateAndConsume(String token, Long internId) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Mã xác thực phiên QR (qrToken) không được để trống");
        }

        CheckInSession session = sessionCache.get(token);
        if (session == null) {
            throw new BadRequestException("Mã QR xác thực không hợp lệ hoặc phiên điểm danh không tồn tại");
        }

        if (!session.getInternId().equals(internId)) {
            log.warn("Cảnh báo bảo mật: Intern ID {} cố tình dùng token {} của Intern ID {}",
                    internId, token, session.getInternId());
            throw new AccessDeniedException("Mã QR xác thực không thuộc về phiên của tài khoản này");
        }

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        if (session.isExpired(now)) {
            sessionCache.remove(token);
            throw new BadRequestException("Mã QR xác thực đã hết hạn (chỉ có hiệu lực trong 60 giây). Vui lòng quét lại vị trí và thử lại.");
        }

        if (session.isConsumed()) {
            throw new BadRequestException("Mã QR xác thực này đã được sử dụng trước đó");
        }

        session.consumed = true;
        sessionCache.remove(token);
        log.info("Xác thực và tiêu thụ thành công phiên QR [token: {}] cho Intern ID {}", token, internId);
        return session;
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        sessionCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now) || entry.getValue().isConsumed());
    }
}
