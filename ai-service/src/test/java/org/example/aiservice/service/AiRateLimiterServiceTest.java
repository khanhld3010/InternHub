package org.example.aiservice.service;

import org.example.aiservice.entity.AiFeatureProfile;
import org.example.aiservice.repository.AiFeatureProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiRateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AiFeatureProfileRepository profileRepository;

    @InjectMocks
    private AiRateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Thành công: Request hợp lệ nằm trong hạn mức phút và ngày")
    void checkFeatureRateLimit_withinLimit_shouldPass() {
        AiFeatureProfile profile = AiFeatureProfile.builder()
                .featureKey("INTERVIEW_GENERATOR")
                .featureName("Sinh Bộ Câu Hỏi Phỏng Vấn")
                .rateLimitPerMinute(5)
                .dailyQuota(50)
                .isActive(true)
                .build();
        when(profileRepository.findByFeatureKey("INTERVIEW_GENERATOR")).thenReturn(Optional.of(profile));

        // Lần gọi thứ 1
        when(valueOperations.increment(startsWith("rate:ai:interview_generator:"))).thenReturn(1L);
        when(valueOperations.increment(startsWith("quota:ai:interview_generator:"))).thenReturn(1L);

        rateLimiterService.checkFeatureRateLimit("INTERVIEW_GENERATOR", "user-123");

        verify(redisTemplate).expire(startsWith("rate:ai:interview_generator:"), any(Duration.class));
        verify(redisTemplate).expire(startsWith("quota:ai:interview_generator:"), any(Duration.class));
    }

    @Test
    @DisplayName("Bị chặn: Vượt quá giới hạn request theo phút (HTTP 429)")
    void checkFeatureRateLimit_exceedMinuteLimit_shouldThrowTooManyRequests() {
        AiFeatureProfile profile = AiFeatureProfile.builder()
                .featureKey("CV_SCREENING")
                .featureName("Sàng Lọc Hồ Sơ & CV")
                .rateLimitPerMinute(3)
                .dailyQuota(10)
                .isActive(true)
                .build();
        when(profileRepository.findByFeatureKey("CV_SCREENING")).thenReturn(Optional.of(profile));

        // Lần gọi thứ 4 (vượt quá 3 req/phút)
        when(valueOperations.increment(startsWith("rate:ai:cv_screening:"))).thenReturn(4L);

        assertThatThrownBy(() -> rateLimiterService.checkFeatureRateLimit("CV_SCREENING", "hr-01"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(rse.getReason()).contains("vượt quá giới hạn 3 lượt/phút");
                });
    }

    @Test
    @DisplayName("Bị chặn: Vượt quá hạn ngạch request theo ngày (HTTP 429)")
    void checkFeatureRateLimit_exceedDailyQuota_shouldThrowTooManyRequests() {
        AiFeatureProfile profile = AiFeatureProfile.builder()
                .featureKey("REPORT_ASSISTANT")
                .featureName("Trợ Lý Đánh Giá Báo Cáo Tuần")
                .rateLimitPerMinute(20)
                .dailyQuota(100)
                .isActive(true)
                .build();
        when(profileRepository.findByFeatureKey("REPORT_ASSISTANT")).thenReturn(Optional.of(profile));

        // Phút hợp lệ (2 req/phút) nhưng ngày đã đạt 101 req/ngày
        when(valueOperations.increment(startsWith("rate:ai:report_assistant:"))).thenReturn(2L);
        when(valueOperations.increment(startsWith("quota:ai:report_assistant:"))).thenReturn(101L);

        assertThatThrownBy(() -> rateLimiterService.checkFeatureRateLimit("REPORT_ASSISTANT", "mentor-99"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(rse.getReason()).contains("hết hạn mức 100 lượt/ngày");
                });
    }

    @Test
    @DisplayName("Bị chặn: Profile đang ở trạng thái Tắt/Inactive (HTTP 403)")
    void checkFeatureRateLimit_profileInactive_shouldThrowForbidden() {
        AiFeatureProfile profile = AiFeatureProfile.builder()
                .featureKey("INTERVIEW_GENERATOR")
                .featureName("Sinh Bộ Câu Hỏi Phỏng Vấn")
                .isActive(false)
                .build();
        when(profileRepository.findByFeatureKey("INTERVIEW_GENERATOR")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> rateLimiterService.checkFeatureRateLimit("INTERVIEW_GENERATOR", "user-123"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("hiện đang tạm tắt bởi Quản trị viên");
                });
    }

    @Test
    @DisplayName("Bảo mật Fail-Closed: Khi Redis gặp sự cố kết nối -> ném HTTP 503 SERVICE_UNAVAILABLE ngay lập tức")
    void checkFeatureRateLimit_redisDown_shouldThrowServiceUnavailable_failClosed() {
        AiFeatureProfile profile = AiFeatureProfile.builder()
                .featureKey("CHATBOT")
                .featureName("Trợ Lý FAQ Thực Tập")
                .rateLimitPerMinute(20)
                .dailyQuota(200)
                .isActive(true)
                .build();
        when(profileRepository.findByFeatureKey("CHATBOT")).thenReturn(Optional.of(profile));

        // Redis down ném lỗi kết nối
        when(valueOperations.increment(anyString())).thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThatThrownBy(() -> rateLimiterService.checkFeatureRateLimit("CHATBOT", "intern-456"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(rse.getReason()).contains("Hệ thống kiểm soát lưu lượng AI tạm thời không khả dụng");
                });
    }
}
