package org.example.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    /**
     * KeyResolver xác định client dựa trên IP nguồn (hỗ trợ cả header X-Forwarded-For)
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
                return Mono.just(forwardedFor.split(",")[0].trim());
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just(remoteAddress.getAddress().getHostAddress());
            }
            return Mono.just("anonymous-client");
        };
    }
}
