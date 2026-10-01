package org.example.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.config.RedisConfig;
import org.example.notificationservice.dto.CreateNotificationRequest;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.entity.Notification;
import org.example.notificationservice.exception.ForbiddenException;
import org.example.notificationservice.exception.ResourceNotFoundException;
import org.example.notificationservice.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public NotificationResponse createAndDispatch(CreateNotificationRequest request) {
        Notification notification = Notification.builder()
                .recipientId(request.getRecipientId())
                .actorId(request.getActorId())
                .title(request.getTitle())
                .content(request.getContent())
                .type(request.getType())
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .actionUrl(request.getActionUrl())
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = mapToResponse(saved);

        // Publish to Redis Pub/Sub for real-time fan-out
        try {
            redisTemplate.convertAndSend(RedisConfig.TOPIC_NOTIFICATIONS, response);
        } catch (Exception e) {
            log.error("Failed to publish notification to Redis Pub/Sub: {}", e.getMessage(), e);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId, LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(limit, 50));
        List<Notification> list;

        if (cursorCreatedAt != null && cursorId != null) {
            list = notificationRepository.findByRecipientIdWithCursor(userId, cursorCreatedAt, cursorId, pageable);
        } else {
            list = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(userId, pageable);
        }

        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long currentUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        // IDOR Prevention: Verify ownership
        if (!notification.getRecipientId().equals(currentUserId)) {
            throw new ForbiddenException("Không có quyền thao tác trên thông báo này");
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }

        return mapToResponse(notification);
    }

    @Transactional
    public int markAllAsRead(Long currentUserId) {
        return notificationRepository.markAllAsRead(currentUserId);
    }

    public void dispatchSecurityCommand(org.example.notificationservice.dto.SecurityCommandMessage command) {
        try {
            redisTemplate.convertAndSend(RedisConfig.TOPIC_SECURITY_COMMANDS, command);
            log.info("Dispatched security command via Redis Pub/Sub: action={}, userId={}", command.getAction(), command.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish security command to Redis Pub/Sub: {}", e.getMessage(), e);
        }
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .actorId(n.getActorId())
                .title(n.getTitle())
                .content(n.getContent())
                .type(n.getType())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .actionUrl(n.getActionUrl())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
