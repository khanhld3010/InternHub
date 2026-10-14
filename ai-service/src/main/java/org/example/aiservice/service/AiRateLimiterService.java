package org.example.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiRateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final int LIMIT_PER_MINUTE = 20;
    private static final int LIMIT_PER_DAY = 200;

    /**
     * Kiểm tra và tăng lượt đếm rate limit cho userId.
     * Áp dụng nguyên tắc Fail-Closed: nếu Redis lỗi -> ném lỗi 503 ngay, không bypass.
     */
    public void checkAndIncrement(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Khong xac dinh duoc nguoi dung");
        }

        try {
            // 1. Fixed-window 60s (20 requests/phút)
            String minuteKey = "rate:ai_chat:" + userId;
            Long currentMinuteCount = redisTemplate.opsForValue().increment(minuteKey);
            if (currentMinuteCount != null && currentMinuteCount == 1) {
                redisTemplate.expire(minuteKey, Duration.ofSeconds(60));
            }
            if (currentMinuteCount != null && currentMinuteCount > LIMIT_PER_MINUTE) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Ban da vuot qua gioi han 20 tin nhan/phut. Vui long doi giay lat!"
                );
            }

            // 2. Quota theo ngày (200 requests/ngày)
            String todayStr = LocalDate.now().format(DATE_FORMATTER);
            String dayKey = "quota:ai_chat:" + userId + ":" + todayStr;
            Long currentDayCount = redisTemplate.opsForValue().increment(dayKey);
            if (currentDayCount != null && currentDayCount == 1) {
                redisTemplate.expire(dayKey, Duration.ofHours(25));
            }
            if (currentDayCount != null && currentDayCount > LIMIT_PER_DAY) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Ban da dung het quota 200 tin nhan trong ngay hom nay. Vui long quay lai vao ngay mai!"
                );
            }

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            // Redis connection failure / timeout -> FAIL-CLOSED
            log.error("Loi ket noi Redis trong RateLimiter (Fail-closed): {}", ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "He thong kiem soat luu luong AI tam thoi khong kha dung. Vui long thu lai sau."
            );
        }
    }
}
