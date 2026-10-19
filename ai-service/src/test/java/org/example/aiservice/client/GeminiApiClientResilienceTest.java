package org.example.aiservice.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD Test Suite cho AI-20: Resilience4j Circuit Breaker & Fallback cho GeminiApiClient.
 */
@ExtendWith(MockitoExtension.class)
class GeminiApiClientResilienceTest {

    private CircuitBreaker circuitBreaker;

    @Mock
    private GeminiApiClient geminiApiClient;

    @BeforeEach
    void setUp() {
        lenient().when(geminiApiClient.fallbackGenerateContent(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenCallRealMethod();

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordExceptions(RestClientResponseException.class, RuntimeException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        circuitBreaker = registry.circuitBreaker("geminiClientTest");
    }

    @Test
    @DisplayName("TDD RED 1: Khi gọi bình thường, Circuit Breaker ở trạng thái CLOSED và trả về kết quả")
    void shouldStayInClosedState_whenCallsSucceed() {
        // Arrange
        when(geminiApiClient.generateContent(anyString(), anyString(), anyString(), anyList(), anyString(), any(), anyInt()))
                .thenReturn("Phản hồi từ Gemini AI");

        // Act
        String result = circuitBreaker.executeSupplier(() ->
                geminiApiClient.generateContent("dummyKey", "gemini-1.5-flash", "Sys", Collections.emptyList(), "Hello", BigDecimal.valueOf(0.7), 1000)
        );

        // Assert
        assertThat(result).isEqualTo("Phản hồi từ Gemini AI");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("TDD RED 2: Khi lỗi vượt ngưỡng (>= 50%), Circuit Breaker chuyển sang OPEN và kích hoạt Fallback")
    void shouldTripToOpenStateAndExecuteFallback_whenFailureRateExceedsThreshold() {
        // Arrange
        when(geminiApiClient.generateContent(anyString(), anyString(), anyString(), anyList(), anyString(), any(), anyInt()))
                .thenThrow(new RuntimeException("Google API Service Unavailable 503"));

        // Act: Gây lỗi 2 lần liên tiếp để vượt ngưỡng failure rate (50%)
        for (int i = 0; i < 2; i++) {
            try {
                circuitBreaker.executeSupplier(() ->
                        geminiApiClient.generateContent("key", "model", "sys", Collections.emptyList(), "Hi", BigDecimal.valueOf(0.7), 1000)
                );
            } catch (Exception ignored) {
            }
        }

        // Assert: Mạch phải chuyển sang OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Khi mạch OPEN, request tiếp theo bị fail-fast với CallNotPermittedException
        String fallbackResponse;
        try {
            circuitBreaker.executeSupplier(() ->
                    geminiApiClient.generateContent("key", "model", "sys", Collections.emptyList(), "Hi", BigDecimal.valueOf(0.7), 1000)
            );
            fallbackResponse = "Thành công ngoài mong đợi";
        } catch (CallNotPermittedException ex) {
            // Khi Circuit Breaker bắt được CallNotPermittedException, phương thức fallback sẽ được gọi
            fallbackResponse = geminiApiClient.fallbackGenerateContent("key", "model", "sys", Collections.emptyList(), "Hi", BigDecimal.valueOf(0.7), 1000, ex);
        }

        assertThat(fallbackResponse).contains("bảo trì");
    }
}
