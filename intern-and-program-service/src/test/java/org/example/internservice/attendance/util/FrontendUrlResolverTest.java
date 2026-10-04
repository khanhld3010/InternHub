package org.example.internservice.attendance.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontendUrlResolverTest {

    private final FrontendUrlResolver resolver = new FrontendUrlResolver("http://localhost:5173/");

    @Test
    @DisplayName("Tier 1: clientBaseUrl được ưu tiên cao nhất khi hợp lệ")
    void resolveBaseUrl_whenClientBaseUrlProvided_shouldUseIt() {
        String result = resolver.resolveBaseUrl("http://192.168.1.100:5173/");
        assertEquals("http://192.168.1.100:5173", result);
    }

    @Test
    @DisplayName("Tier 2a: Header Origin được dùng khi không có clientBaseUrl")
    void resolveBaseUrl_whenOriginPresent_shouldUseOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://192.168.1.50:5173");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            String result = resolver.resolveBaseUrl(null);
            assertEquals("http://192.168.1.50:5173", result);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("Tier 2b: Header Referer được trích xuất khi không có Origin")
    void resolveBaseUrl_whenRefererPresent_shouldExtractHost() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "http://192.168.1.75:5173/intern/dashboard");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            String result = resolver.resolveBaseUrl("");
            assertEquals("http://192.168.1.75:5173", result);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("Tier 3: Fallback về default frontend url khi không có header nào")
    void resolveBaseUrl_whenNoContext_shouldFallbackToDefault() {
        RequestContextHolder.resetRequestAttributes();
        String result = resolver.resolveBaseUrl(null);
        assertEquals("http://localhost:5173", result);
    }

    @Test
    @DisplayName("buildConfirmationUrl ghép đúng định dạng đường dẫn")
    void buildConfirmationUrl_shouldFormatCorrectly() {
        String url = resolver.buildConfirmationUrl("http://localhost:5173/", "test-token-123");
        assertEquals("http://localhost:5173/intern/attendance/confirm?token=test-token-123", url);
    }
}
