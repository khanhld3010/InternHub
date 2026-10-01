package org.example.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.example.notificationservice.websocket.StompSecurityChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompSecurityChannelInterceptor securityChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Destination prefixes cho subscriber
        registry.enableSimpleBroker("/topic", "/queue", "/user");
        // Prefix cho destination từ client gửi lên server
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix cho user-specific destinations (/user/{username}/queue/...)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket chính thức kèm SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(securityChannelInterceptor);
    }
}
