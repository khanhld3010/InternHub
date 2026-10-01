package org.example.notificationservice.websocket;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.security.JwtTokenProvider;
import org.example.notificationservice.security.UserPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompSecurityChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        // 1. Chặn và xác thực tại frame CONNECT
        if (StompCommand.CONNECT.equals(command)) {
            handleConnect(accessor);
        }
        // 2. Chặn và phân quyền tại frame SUBSCRIBE (Chống IDOR qua Topic)
        else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            throw new AccessDeniedException("Missing Authorization header in STOMP connect");
        }

        String bearerToken = authHeaders.get(0);
        if (!bearerToken.startsWith("Bearer ")) {
            throw new AccessDeniedException("Invalid Bearer token prefix in STOMP connect");
        }

        String token = bearerToken.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new AccessDeniedException("Invalid or expired JWT token");
        }

        Long userId = jwtTokenProvider.extractUserId(token);
        String role = jwtTokenProvider.extractRole(token);
        Date expiration = jwtTokenProvider.extractExpiration(token);

        UserPrincipal principal = new UserPrincipal(userId, role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, null);
        accessor.setUser(auth);

        // Lưu vào Session Registry
        String sessionId = accessor.getSessionId();
        WebSocketSessionMeta meta = WebSocketSessionMeta.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(role)
                .tokenExpirationTime(expiration)
                .lastValidatedAt(System.currentTimeMillis())
                .build();
        sessionRegistry.registerSession(sessionId, meta);
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Object userObj = accessor.getUser();
        if (!(userObj instanceof UsernamePasswordAuthenticationToken authToken)) {
            throw new AccessDeniedException("Unauthenticated subscription attempt");
        }

        UserPrincipal principal = (UserPrincipal) authToken.getPrincipal();
        String role = principal.getRole();

        // Phân quyền cho Topic broadcast (VD: /topic/live/applications, /topic/live/contracts)
        if (destination.startsWith("/topic/live/")) {
            boolean isStaffOrAdmin = role.contains("ADMIN") || role.contains("HR") || role.contains("MANAGER");
            if (!isStaffOrAdmin) {
                log.warn("Access denied for user {} (role {}) subscribing to topic {}", principal.getUserId(), role, destination);
                throw new AccessDeniedException("Forbidden: You do not have permission to subscribe to " + destination);
            }
        }
    }
}
