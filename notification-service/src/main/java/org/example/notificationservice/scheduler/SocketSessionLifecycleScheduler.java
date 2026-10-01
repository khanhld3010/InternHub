package org.example.notificationservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.SecurityCommandMessage;
import org.example.notificationservice.websocket.WebSocketSessionMeta;
import org.example.notificationservice.websocket.WebSocketSessionRegistry;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SocketSessionLifecycleScheduler {

    private final WebSocketSessionRegistry sessionRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 60000)
    public void scanAndValidateSessions() {
        Map<String, WebSocketSessionMeta> activeSessions = sessionRegistry.getAllSessions();
        if (activeSessions.isEmpty()) {
            return;
        }

        Date now = new Date();
        List<WebSocketSessionMeta> expiredSessions = new ArrayList<>();
        Set<Long> uniqueUserIds = new HashSet<>();

        for (WebSocketSessionMeta meta : activeSessions.values()) {
            // 1. Kiểm tra JWT token expiration
            if (meta.getTokenExpirationTime() != null && meta.getTokenExpirationTime().before(now)) {
                expiredSessions.add(meta);
            } else {
                uniqueUserIds.add(meta.getUserId());
            }
        }

        // Xử lý các session hết hạn token
        for (WebSocketSessionMeta expired : expiredSessions) {
            handleSessionExpired(expired, "TOKEN_EXPIRED", "Phiên xác thực đã hết hạn");
        }

        // 2. Batch check Revocation status trong Redis bằng Pipeline (1 network round-trip)
        if (!uniqueUserIds.isEmpty()) {
            List<Long> userList = new ArrayList<>(uniqueUserIds);
            List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Long uid : userList) {
                    byte[] key = ("security:revoked:" + uid).getBytes();
                    connection.keyCommands().exists(key);
                }
                return null;
            });

            for (int i = 0; i < userList.size(); i++) {
                Object res = pipelineResults.get(i);
                if (Boolean.TRUE.equals(res)) {
                    Long revokedUserId = userList.get(i);
                    log.warn("Scheduler detected revoked user session: {}", revokedUserId);
                    sessionRegistry.getSessionsByUserId(revokedUserId).forEach(meta -> {
                        handleSessionExpired(meta, "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa hoặc thu hồi quyền");
                    });
                }
            }
        }
    }

    private void handleSessionExpired(WebSocketSessionMeta meta, String action, String reason) {
        log.info("Terminating session {} for user {}: {}", meta.getSessionId(), meta.getUserId(), action);
        SecurityCommandMessage msg = SecurityCommandMessage.builder()
                .action(action)
                .userId(meta.getUserId())
                .reason(reason)
                .message(reason)
                .build();

        // 1. Gửi frame STOMP báo lý do
        messagingTemplate.convertAndSendToUser(
                String.valueOf(meta.getUserId()),
                "/queue/security",
                msg
        );

        // 2. Delay 500ms dọn session
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
            sessionRegistry.removeSession(meta.getSessionId());
        });
    }
}
