package org.example.internservice.intern.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.intern.client.IntegrationEmailClient;
import org.example.internservice.intern.event.InternMentorAssignedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternMentorAssignedEventListener {

    private final IntegrationEmailClient emailClient;
    private final org.example.internservice.intern.client.NotificationEventDispatcher notificationDispatcher;
    private final org.example.internservice.intern.client.IdentityServiceClient identityServiceClient;

    @Async
    @EventListener
    public void handleInternMentorAssigned(InternMentorAssignedEvent event) {
        log.info("Bat duoc su kien InternMentorAssignedEvent: type={}, internId={}, internCode={}",
                event.getEventType(), event.getInternProfileId(), event.getInternCode());

        Map<String, Object> payload = new HashMap<>();
        String idempotencyKey = "MENTOR-" + event.getEventType() + "-" + event.getInternProfileId() + "-" + UUID.randomUUID();

        payload.put("idempotencyKey", idempotencyKey);
        payload.put("internProfileId", event.getInternProfileId());
        payload.put("internCode", event.getInternCode());
        payload.put("internName", event.getInternName());
        payload.put("internEmail", event.getInternEmail());
        payload.put("programName", event.getProgramName());
        payload.put("appliedPosition", event.getAppliedPosition());
        payload.put("eventType", event.getEventType());
        payload.put("newMentorId", event.getNewMentorId());
        payload.put("newMentorName", event.getNewMentorName());
        payload.put("newMentorEmail", event.getNewMentorEmail());
        payload.put("oldMentorId", event.getOldMentorId());
        payload.put("oldMentorName", event.getOldMentorName());
        payload.put("oldMentorEmail", event.getOldMentorEmail());
        payload.put("reason", event.getReason());
        payload.put("notes", event.getNotes());
        payload.put("actorUsername", event.getActorUsername());

        emailClient.sendMentorAssignmentEmail(payload);

        // Bắn thông báo real-time tới TTS và Mentor
        dispatchMentorNotifications(event);
    }

    private void dispatchMentorNotifications(InternMentorAssignedEvent event) {
        Long internUserId = resolveUserId(event.getInternEmail());
        Long newMentorUserId = resolveUserId(event.getNewMentorEmail());
        Long oldMentorUserId = resolveUserId(event.getOldMentorEmail());

        String eventType = event.getEventType(); // ASSIGNED, REPLACED, REVOKED

        if ("ASSIGNED".equalsIgnoreCase(eventType)) {
            // 1. Gửi cho TTS: MENTOR_ASSIGNED
            if (internUserId != null) {
                notificationDispatcher.dispatch(org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                        .recipientId(internUserId)
                        .title("Phân công người hướng dẫn")
                        .content(String.format("Bạn được phân công Mentor %s (%s) hướng dẫn trong chương trình thực tập.",
                                event.getNewMentorName(), event.getNewMentorEmail()))
                        .type("MENTOR_ASSIGNED")
                        .referenceType("MENTOR_ASSIGNMENT")
                        .referenceId(String.valueOf(event.getInternProfileId()))
                        .actionUrl("/profile")
                        .build());
            }
            // 2. Gửi cho Mentor: INTERN_ASSIGNED_TO_MENTOR
            if (newMentorUserId != null) {
                notificationDispatcher.dispatch(org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                        .recipientId(newMentorUserId)
                        .title("Tiếp nhận thực tập sinh mới")
                        .content(String.format("Bạn được phân công hướng dẫn TTS %s (%s) cho vị trí %s.",
                                event.getInternName(), event.getInternEmail(), event.getAppliedPosition()))
                        .type("INTERN_ASSIGNED_TO_MENTOR")
                        .referenceType("MENTOR_ASSIGNMENT")
                        .referenceId(String.valueOf(event.getInternProfileId()))
                        .actionUrl("/mentor/interns")
                        .build());
            }
        } else if ("REPLACED".equalsIgnoreCase(eventType)) {
            // 1. Gửi cho TTS
            if (internUserId != null) {
                notificationDispatcher.dispatch(org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                        .recipientId(internUserId)
                        .title("Thay đổi người hướng dẫn")
                        .content(String.format("Mentor hướng dẫn của bạn đã được thay đổi sang %s (%s).",
                                event.getNewMentorName(), event.getNewMentorEmail()))
                        .type("MENTOR_ASSIGNED")
                        .referenceType("MENTOR_ASSIGNMENT")
                        .referenceId(String.valueOf(event.getInternProfileId()))
                        .actionUrl("/profile")
                        .build());
            }
            // 2. Gửi cho Mentor mới
            if (newMentorUserId != null) {
                notificationDispatcher.dispatch(org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                        .recipientId(newMentorUserId)
                        .title("Tiếp nhận bàn giao TTS")
                        .content(String.format("Bạn tiếp nhận hướng dẫn TTS %s (%s) bàn giao từ Mentor %s.",
                                event.getInternName(), event.getInternEmail(), event.getOldMentorName()))
                        .type("MENTOR_HANDOVER")
                        .referenceType("MENTOR_ASSIGNMENT")
                        .referenceId(String.valueOf(event.getInternProfileId()))
                        .actionUrl("/mentor/interns")
                        .build());
            }
            // 3. Gửi cho Mentor cũ
            if (oldMentorUserId != null) {
                notificationDispatcher.dispatch(org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                        .recipientId(oldMentorUserId)
                        .title("Bàn giao thực tập sinh")
                        .content(String.format("TTS %s đã được bàn giao sang Mentor %s.",
                                event.getInternName(), event.getNewMentorName()))
                        .type("MENTOR_HANDOVER")
                        .referenceType("MENTOR_ASSIGNMENT")
                        .referenceId(String.valueOf(event.getInternProfileId()))
                        .actionUrl("/mentor/interns")
                        .build());
            }
        } else if ("REVOKED".equalsIgnoreCase(eventType)) {
            if (internUserId != null) {
                notificationDispatcher.dispatch(org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                        .recipientId(internUserId)
                        .title("Thu hồi phân công Mentor")
                        .content(String.format("Phân công hướng dẫn với Mentor %s đã kết thúc. Lý do: %s.",
                                event.getOldMentorName(), event.getReason()))
                        .type("MENTOR_REVOKED")
                        .referenceType("MENTOR_ASSIGNMENT")
                        .referenceId(String.valueOf(event.getInternProfileId()))
                        .actionUrl("/profile")
                        .build());
            }
        }
    }

    private Long resolveUserId(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return identityServiceClient.findUserIdByEmail(email);
    }
}
