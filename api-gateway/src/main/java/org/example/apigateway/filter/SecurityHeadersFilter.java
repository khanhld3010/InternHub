package org.example.apigateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filter thiết lập các HTTP Security Headers an toàn cho các yêu cầu HTTP.
 * Tự động bỏ qua các yêu cầu WebSocket / SockJS handshake để tránh lỗi commit response của Reactor Netty.
 */
@Component
public class SecurityHeadersFilter implements WebFilter, Ordered {

    private static final String CSP_POLICY =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' "
            + "https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; "
            + "img-src 'self' data: blob: http: https:; connect-src 'self' ws: wss: http: https:;";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String upgrade = exchange.getRequest().getHeaders().getFirst(HttpHeaders.UPGRADE);
        String path = exchange.getRequest().getPath().value();

        // Không gắn HTTP response header cho luồng nâng cấp giao thức WebSocket / SockJS
        if ("websocket".equalsIgnoreCase(upgrade) || path.startsWith("/ws")) {
            return chain.filter(exchange);
        }

        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set("X-Frame-Options", "DENY");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-XSS-Protection", "1; mode=block");
            headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
            headers.set("Content-Security-Policy", CSP_POLICY);
            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
