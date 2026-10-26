package org.example.notificationservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.config.RedisConfig;
import org.example.notificationservice.dto.MissionItemSyncEvent;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.dto.SecurityCommandMessage;
import org.example.notificationservice.service.NotificationService;
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
    private final ChannelTopic aiJobTopic;
    private final ChannelTopic proctoringEventTopic;
    private final ChannelTopic missionEventTopic;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        container.addMessageListener(this, notificationTopic);
        container.addMessageListener(this, securityCommandTopic);
        container.addMessageListener(this, aiJobTopic);
        container.addMessageListener(this, proctoringEventTopic);
        container.addMessageListener(this, missionEventTopic);
        log.info("RedisMessageSubscriber registered for topics: {}, {}, {}, {}, {}",
                notificationTopic.getTopic(), securityCommandTopic.getTopic(), aiJobTopic.getTopic(), proctoringEventTopic.getTopic(), missionEventTopic.getTopic());
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), java.nio.charset.StandardCharsets.UTF_8);
        String body = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);

        try {
            if (channel.equals(RedisConfig.TOPIC_NOTIFICATIONS)) {
                handleNotificationMessage(body);
            } else if (channel.equals(RedisConfig.TOPIC_SECURITY_COMMANDS)) {
                handleSecurityCommandMessage(body);
            } else if (channel.equals(RedisConfig.TOPIC_AI_JOBS)) {
                handleAiJobMessage(body);
            } else if (channel.equals(RedisConfig.TOPIC_PROCTORING_EVENTS)) {
                handleProctoringEventMessage(body);
            } else if (channel.equals(RedisConfig.TOPIC_MISSION_EVENTS)) {
                handleMissionEventMessage(body);
            }
        } catch (Exception e) {
            log.error("Error processing Redis message on channel {}: {}", channel, e.getMessage(), e);
        }
    }

    private void handleProctoringEventMessage(String body) throws Exception {
        org.example.notificationservice.dto.ProctoringAlertEvent event =
                objectMapper.readValue(body, org.example.notificationservice.dto.ProctoringAlertEvent.class);

        // 1. Phân phối tới kênh Phòng Giám Sát Ca Thi của HR & Mentor (/topic/proctoring/schedule/{scheduleId})
        if (event.getScheduleId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/proctoring/schedule/" + event.getScheduleId(),
                    event
            );
        }

        // 2. Phân phối trực tiếp tới phiên làm bài của thí sinh (/topic/proctoring/session/{sessionId})
        if (event.getSessionId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/proctoring/session/" + event.getSessionId(),
                    event
            );
        }

        log.info("Distributed proctoring alert: scheduleId={}, sessionId={}, candidateName={}, eventType={}, strikes={}/{}",
                event.getScheduleId(), event.getSessionId(), event.getCandidateName(),
                event.getEventType(), event.getStrikeCount(), event.getMaxStrikes());
    }

    private void handleAiJobMessage(String body) throws Exception {
        org.example.notificationservice.dto.AiJobProgressEvent event =
                objectMapper.readValue(body, org.example.notificationservice.dto.AiJobProgressEvent.class);

        // 1. Dual-Channel: Kênh riêng tư của user (/user/{userId}/queue/ai-jobs)
        if (event.getUserId() != null) {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(event.getUserId()),
                    "/queue/ai-jobs",
                    event
            );
        }

        // 2. Dual-Channel: Kênh broadcast theo phòng của Job (/topic/ai-jobs/{jobId})
        if (event.getJobId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/ai-jobs/" + event.getJobId(),
                    event
            );
        }

        // 3. Cơ chế Thông Báo Kép Hybrid (Lưu vào DB Notification khi COMPLETED hoặc FAILED)
        if (event.getUserId() != null && ("COMPLETED".equalsIgnoreCase(event.getStatus()) || "FAILED".equalsIgnoreCase(event.getStatus()))) {
            try {
                boolean isSuccess = "COMPLETED".equalsIgnoreCase(event.getStatus());
                String title = isSuccess ? "Tác vụ AI hoàn tất" : "Tác vụ AI thất bại";
                String content = isSuccess
                        ? (event.getCurrentStep() != null ? event.getCurrentStep() : "Tác vụ xử lý AI đã hoàn thành thành công.")
                        : (event.getErrorMessage() != null ? event.getErrorMessage() : "Tác vụ xử lý AI gặp lỗi.");

                org.example.notificationservice.dto.CreateNotificationRequest notifReq =
                        org.example.notificationservice.dto.CreateNotificationRequest.builder()
                                .recipientId(event.getUserId())
                                .actorId(0L) // System/AI Actor
                                .title(title)
                                .content(content)
                                .type(isSuccess ? "AI_SUCCESS" : "AI_ERROR")
                                .referenceType(event.getJobType())
                                .referenceId(event.getReferenceId() != null ? event.getReferenceId() : event.getJobId())
                                .actionUrl("/hr/interview-management")
                                .build();

                notificationService.createAndDispatch(notifReq);
                log.info("Persisted hybrid DB notification for finished AI job: jobId={}, status={}",
                        event.getJobId(), event.getStatus());
            } catch (Exception e) {
                log.error("Failed to persist hybrid DB notification for AI Job {}: {}",
                        event.getJobId(), e.getMessage(), e);
            }
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

    private void handleMissionEventMessage(String body) throws Exception {
        MissionItemSyncEvent event = objectMapper.readValue(body, MissionItemSyncEvent.class);
        log.info("Processing MissionItemSyncEvent: action={}, itemId={}, boardId={}, actorId={}, assignees={}",
                event.getAction(), event.getItemId(), event.getBoardId(), event.getActorId(), event.getAssigneeIds());

        // 1. Phân phối STOMP Channel Board cho Mentor & Workspace chung
        if (event.getBoardId() != null) {
            String boardTopic = "/topic/missions/board/" + event.getBoardId();
            messagingTemplate.convertAndSend(boardTopic, event);
            log.info("Dispatched MissionItemSyncEvent to Board channel: {}", boardTopic);
        }

        // 2. Phân phối STOMP Channel Intern cá nhân cho từng TTS được giao
        if (event.getAssigneeIds() != null && !event.getAssigneeIds().isEmpty()) {
            for (Long internId : event.getAssigneeIds()) {
                if (internId != null) {
                    String internTopic = "/topic/missions/intern/" + internId;
                    messagingTemplate.convertAndSend(internTopic, event);
                    log.info("Dispatched MissionItemSyncEvent to Intern personal channel: {}", internTopic);
                }
            }
        }
    }
}
