package org.example.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Gắn đầy đủ Security Headers khi request là HTTP thông thường")
    void testFilter_StandardHttpRequest_SetsSecurityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        // Trigger commit actions on mock response
        exchange.getResponse().setComplete().block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("1; mode=block", headers.getFirst("X-XSS-Protection"));
        assertEquals("strict-origin-when-cross-origin", headers.getFirst("Referrer-Policy"));
        assertNotNull(headers.getFirst("Content-Security-Policy"));
        verify(chain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("Bỏ qua không can thiệp headers khi request có header Upgrade: websocket")
    void testFilter_WebSocketUpgradeRequest_SkipsHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/some/endpoint")
                .header(HttpHeaders.UPGRADE, "websocket")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();
        exchange.getResponse().setComplete().block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertNull(headers.getFirst("X-Frame-Options"));
        assertNull(headers.getFirst("Content-Security-Policy"));
        verify(chain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("Bỏ qua không can thiệp headers khi đường dẫn bắt đầu bằng /ws")
    void testFilter_WebSocketPath_SkipsHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/ws/info").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();
        exchange.getResponse().setComplete().block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertNull(headers.getFirst("X-Frame-Options"));
        verify(chain, times(1)).filter(exchange);
    }
}
