package org.example.internservice.attendance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý phiên QR Check-in linh hoạt qua Redis Broker (Cluster-ready) và in-memory fallback.
 * Hỗ trợ TTL 60 giây + Grace skew 15 giây + One-time Nonce (ADR 0003 & TM-25 v2.0).
 */
@Component
@Slf4j
public class CheckInQrTokenManager {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int TTL_SECONDS = 60;
    public static final int GRACE_SECONDS = 15;
    private static final String REDIS_KEY_PREFIX = "attendance:qr:";

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
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
        private boolean consumed;

        public boolean isExpired(LocalDateTime now) {
            if (expiresAt == null) return false;
            return now.isAfter(expiresAt.plusSeconds(GRACE_SECONDS));
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CheckInSession> fallbackCache = new ConcurrentHashMap<>();

    @Autowired
    public CheckInQrTokenManager(@Autowired(required = false) StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

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

        String redisKey = REDIS_KEY_PREFIX + token;
        boolean savedToRedis = false;
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(session);
                redisTemplate.opsForValue().set(redisKey, json, Duration.ofSeconds(TTL_SECONDS + GRACE_SECONDS));
                savedToRedis = true;
            } catch (Exception e) {
                log.warn("Không thể lưu phiên QR vào Redis, chuyển sang fallback in-memory: {}", e.getMessage());
            }
        }

        if (!savedToRedis) {
            fallbackCache.put(token, session);
        }

        log.info("Đã tạo phiên QR Check-in [token: {}] cho Intern ID {} (hết hạn lúc {})", token, internId, expiresAt);
        return session;
    }

    public CheckInSession validateAndConsume(String token, Long internId) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Mã xác thực phiên QR (qrToken) không được để trống");
        }

        CheckInSession session = null;
        String redisKey = REDIS_KEY_PREFIX + token;

        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(redisKey);
                if (json != null) {
                    session = objectMapper.readValue(json, CheckInSession.class);
                }
            } catch (Exception e) {
                log.warn("Lỗi khi đọc token từ Redis, kiểm tra fallback cache: {}", e.getMessage());
            }
        }

        if (session == null) {
            session = fallbackCache.get(token);
        }

        if (session == null) {
            throw new BadRequestException("Mã QR xác thực không hợp lệ hoặc phiên điểm danh đã hết hạn");
        }

        if (!session.getInternId().equals(internId)) {
            log.warn("Cảnh báo bảo mật: Intern ID {} cố tình dùng token {} của Intern ID {}",
                    internId, token, session.getInternId());
            throw new AccessDeniedException("Mã QR xác thực không thuộc về phiên của tài khoản này");
        }

        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        if (session.isExpired(now)) {
            if (redisTemplate != null) redisTemplate.delete(redisKey);
            fallbackCache.remove(token);
            throw new BadRequestException("Mã QR xác thực đã hết hạn (chỉ có hiệu lực trong 60 giây). Vui lòng quét lại vị trí và thử lại.");
        }

        if (session.isConsumed()) {
            throw new BadRequestException("Mã QR xác thực này đã được sử dụng trước đó");
        }

        session.setConsumed(true);
        if (redisTemplate != null) {
            redisTemplate.delete(redisKey);
        }
        fallbackCache.remove(token);

        log.info("Xác thực và tiêu thụ thành công phiên QR [token: {}] cho Intern ID {}", token, internId);
        return session;
    }
}
