package org.example.internservice.intern.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.intern.client.IntegrationEmailClient;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.event.InternDecisionProcessedEvent;
import org.example.internservice.intern.service.OnboardingTokenService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternDecisionEventListener {

    private final IntegrationEmailClient emailClient;
    private final OnboardingTokenService tokenService;
    private final org.example.internservice.intern.client.NotificationEventDispatcher notificationDispatcher;
    private final org.example.internservice.intern.client.IdentityServiceClient identityServiceClient;

    @Async
    @EventListener
    public void handleInternDecisionProcessed(InternDecisionProcessedEvent event) {
        log.info("Bat duoc su kien InternDecisionProcessedEvent cho ho so ID: {}, Code: {}, Status: {}",
                event.getInternProfileId(), event.getInternCode(), event.getDecision());

        Map<String, Object> payload = new HashMap<>();
        String idempotencyKey = "DECISION-" + event.getInternProfileId() + "-" + event.getDecision() + "-" + UUID.randomUUID();

        payload.put("idempotencyKey", idempotencyKey);
        payload.put("internProfileId", event.getInternProfileId());
        payload.put("internCode", event.getInternCode());
        payload.put("fullName", event.getFullName());
        payload.put("email", event.getEmail());
        payload.put("decision", event.getDecision().name());
        payload.put("rejectionReason", event.getRejectionReason());
        payload.put("appliedPosition", event.getAppliedPosition());
        if (event.getStartDate() != null) {
            payload.put("startDate", event.getStartDate().toString());
        }

        if (event.getDecision() == InternStatus.APPROVED) {
            String onboardingToken = tokenService.generateOnboardingToken(
                    event.getInternProfileId(), event.getEmail(), event.getFullName());
            payload.put("onboardingToken", onboardingToken);
        }

        emailClient.sendInternDecisionEmail(payload);

        // Bắn thông báo real-time tới ứng viên nếu đã có tài khoản người dùng
        dispatchDecisionNotification(event);
    }

    private void dispatchDecisionNotification(InternDecisionProcessedEvent event) {
        Long targetUserId = event.getUserId();
        if (targetUserId == null && event.getEmail() != null) {
            targetUserId = identityServiceClient.findUserIdByEmail(event.getEmail());
        }
        if (targetUserId == null) {
            return;
        }

        if (event.getDecision() == InternStatus.APPROVED) {
            notificationDispatcher.dispatch(org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                    .recipientId(targetUserId)
                    .title("Hồ sơ thực tập được phê duyệt")
                    .content("Chúc mừng! Hồ sơ ứng tuyển của bạn đã được phê duyệt. Vui lòng kiểm tra email để nhận thông tin hướng dẫn tiếp theo.")
                    .type("APPLICATION_APPROVED")
                    .referenceType("APPLICATION")
                    .referenceId(String.valueOf(event.getInternProfileId()))
                    .actionUrl("/profile")
                    .build());
        } else if (event.getDecision() == InternStatus.REJECTED) {
            String reasonText = (event.getRejectionReason() != null && !event.getRejectionReason().isBlank())
                    ? " Lý do: " + event.getRejectionReason()
                    : "";
            notificationDispatcher.dispatch(org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                    .recipientId(targetUserId)
                    .title("Kết quả xét duyệt hồ sơ")
                    .content("Hồ sơ ứng tuyển của bạn chưa phù hợp ở thời điểm này." + reasonText)
                    .type("APPLICATION_REJECTED")
                    .referenceType("APPLICATION")
                    .referenceId(String.valueOf(event.getInternProfileId()))
                    .actionUrl("/profile")
                    .build());
        }
    }
}
