package org.example.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.entity.AiFeatureProfile;
import org.example.aiservice.repository.AiFeatureProfileRepository;
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
    private final AiFeatureProfileRepository profileRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final int DEFAULT_LIMIT_PER_MINUTE = 20;
    private static final int DEFAULT_LIMIT_PER_DAY = 200;

    /**
     * Rate Limiting & Quota Guard đa tầng theo từng Feature Profile (Dynamic Quota).
     * Áp dụng nguyên tắc Fail-Closed: nếu Redis lỗi -> ném HTTP 503 ngay lập tức để bảo vệ tài khoản API Key.
     *
     * @param featureKey Định danh tính năng (CHATBOT, INTERVIEW_GENERATOR, REPORT_ASSISTANT, CV_SCREENING)
     * @param identifier Định danh người dùng (userId) hoặc Client IP
     */
    public void checkFeatureRateLimit(String featureKey, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được danh tính người dùng hoặc IP gọi AI.");
        }

        // Đọc cấu hình hạn ngạch từ Profile (nếu chưa có hoặc lỗi thì dùng default an toàn)
        int limitPerMinute = DEFAULT_LIMIT_PER_MINUTE;
        int limitPerDay = DEFAULT_LIMIT_PER_DAY;
        String featureName = featureKey;

        try {
            AiFeatureProfile profile = profileRepository.findByFeatureKey(featureKey).orElse(null);
            if (profile != null) {
                if (Boolean.FALSE.equals(profile.getIsActive())) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Tính năng AI [" + profile.getFeatureName() + "] hiện đang tạm tắt bởi Quản trị viên."
                    );
                }
                if (profile.getRateLimitPerMinute() != null && profile.getRateLimitPerMinute() > 0) {
                    limitPerMinute = profile.getRateLimitPerMinute();
                }
                if (profile.getDailyQuota() != null && profile.getDailyQuota() > 0) {
                    limitPerDay = profile.getDailyQuota();
                }
                featureName = profile.getFeatureName();
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.warn("[AiRateLimiterService] Không thể tải profile [{}], fallback về default: {}", featureKey, e.getMessage());
        }

        try {
            // 1. Fixed-window 60s
            String minuteKey = "rate:ai:" + featureKey.toLowerCase() + ":" + identifier;
            Long currentMinuteCount = redisTemplate.opsForValue().increment(minuteKey);
            if (currentMinuteCount != null && currentMinuteCount == 1) {
                redisTemplate.expire(minuteKey, Duration.ofSeconds(60));
            }
            if (currentMinuteCount != null && currentMinuteCount > limitPerMinute) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        String.format("Bạn đã vượt quá giới hạn %d lượt/phút cho tính năng [%s]. Vui lòng đợi trong giây lát!",
                                limitPerMinute, featureName)
                );
            }

            // 2. Quota theo ngày (yyyyMMdd)
            String todayStr = LocalDate.now().format(DATE_FORMATTER);
            String dayKey = "quota:ai:" + featureKey.toLowerCase() + ":" + identifier + ":" + todayStr;
            Long currentDayCount = redisTemplate.opsForValue().increment(dayKey);
            if (currentDayCount != null && currentDayCount == 1) {
                redisTemplate.expire(dayKey, Duration.ofHours(25));
            }
            if (currentDayCount != null && currentDayCount > limitPerDay) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        String.format("Bạn đã sử dụng hết hạn mức %d lượt/ngày cho tính năng [%s]. Vui lòng quay lại vào ngày mai!",
                                limitPerDay, featureName)
                );
            }

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            // Redis connection failure / timeout -> FAIL-CLOSED
            log.error("[AiRateLimiterService] Lỗi kết nối Redis khi kiểm tra tính năng [{}] (Fail-closed): {}",
                    featureKey, ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Hệ thống kiểm soát lưu lượng AI tạm thời không khả dụng. Vui lòng thử lại sau."
            );
        }
    }

    /**
     * Tương thích ngược cho Chatbot (chuyển hướng gọi checkFeatureRateLimit).
     */
    public void checkAndIncrement(String userId) {
        checkFeatureRateLimit("CHATBOT", userId);
    }

    /**
     * Rate limit cho Token Exchange: 5 requests / phút / IP (chống Brute-force token)
     */
    public void checkExchangeTokenRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "UNKNOWN_IP";
        }
        try {
            String key = "rate:ai_interview:exchange:" + clientIp;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(60));
            }
            if (count != null && count > 5) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Bạn đã vượt quá giới hạn 5 lần xác thực/phút. Vui lòng đợi trong giây lát!"
                );
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.warn("Redis issue in checkExchangeTokenRateLimit (Fail-open for candidate): {}", ex.getMessage());
        }
    }

    /**
     * Rate limit cho Evidence Telemetry: 10 events / phút / session
     */
    public void checkEvidenceRateLimit(Long sessionId) {
        if (sessionId == null) return;
        try {
            String key = "rate:ai_interview:evidence:" + sessionId;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(60));
            }
            if (count != null && count > 10) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Quá nhiều sự kiện giám sát được gửi liên tục. Vui lòng chờ."
                );
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.warn("Redis issue in checkEvidenceRateLimit (Fail-open): {}", ex.getMessage());
        }
    }
}
