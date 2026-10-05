package org.example.notificationservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.config.RedisConfig;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.dto.SecurityCommandMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisMessageSubscriber implements MessageListener {

    private final RedisMessageListenerContainer container;
    private final ChannelTopic notificationTopic;
    private final ChannelTopic securityCommandTopic;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        container.addMessageListener(this, notificationTopic);
        container.addMessageListener(this, securityCommandTopic);
        log.info("RedisMessageSubscriber registered for topics: {}, {}",
                notificationTopic.getTopic(), securityCommandTopic.getTopic());
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        try {
            if (channel.equals(RedisConfig.TOPIC_NOTIFICATIONS)) {
                handleNotificationMessage(body);
            } else if (channel.equals(RedisConfig.TOPIC_SECURITY_COMMANDS)) {
                handleSecurityCommandMessage(body);
            }
        } catch (Exception e) {
            log.error("Error processing Redis message on channel {}: {}", channel, e.getMessage(), e);
        }
    }

    private void handleNotificationMessage(String body) throws Exception {
        NotificationResponse notification = objectMapper.readValue(body, NotificationResponse.class);
        String destination = "/queue/notifications";
        // Gửi tới user-specific destination: /user/{userId}/queue/notifications
        messagingTemplate.convertAndSendToUser(
                String.valueOf(notification.getRecipientId()),
                destination,
                notification
        );
        log.info("Dispatched notification to user {} via STOMP: {}", notification.getRecipientId(), notification.getId());
    }

    private void handleSecurityCommandMessage(String body) throws Exception {
        SecurityCommandMessage command = objectMapper.readValue(body, SecurityCommandMessage.class);
        String destination = "/queue/security";

        if ("PERMISSION_UPDATED".equalsIgnoreCase(command.getAction())) {
            String targetRole = command.getRole();
            Set<Long> targetUserIds = sessionRegistry.getDistinctUserIdsByRole(targetRole);
            log.info("Received PERMISSION_UPDATED for role {}. Active connected userIds={}", targetRole, targetUserIds);

            for (Long userId : targetUserIds) {
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(userId),
                        destination,
                        command
                );
            }
            return;
        }

        log.warn("Received security command {} for user {}: {}", command.getAction(), command.getUserId(), command.getReason());

        // BƯỚC 1: Đẩy frame STOMP chứa lý do tới user
        if (command.getUserId() != null) {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(command.getUserId()),
                    destination,
                    command
            );

            // BƯỚC 2 & 3: Đợi frame được flush (delay 500ms) rồi dọn session
            CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
                sessionRegistry.getSessionsByUserId(command.getUserId()).forEach(meta -> {
                    sessionRegistry.removeSession(meta.getSessionId());
                });
                log.info("Completed security teardown for user {}", command.getUserId());
            });
        }
    }
}
