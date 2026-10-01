package org.example.internservice.intern.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventDispatcher {

    private final NotificationServiceClient notificationClient;

    @Value("${internal.service-token:internhub-internal-secret-token-2026}")
    private String internalServiceToken;

    @Async
    public void dispatch(CreateNotificationInternalRequest request) {
        if (request == null || request.getRecipientId() == null) {
            log.warn("Cannot dispatch notification with empty request or null recipientId");
            return;
        }
        try {
            notificationClient.sendNotification(internalServiceToken, request);
            log.info("Dispatched notification '{}' to recipientId={}", request.getType(), request.getRecipientId());
        } catch (Exception e) {
            log.warn("Non-blocking failure: Unable to send notification to recipientId={}: {}", request.getRecipientId(), e.getMessage());
        }
    }

    @Async
    public void dispatchToMultiple(java.util.List<Long> recipientIds, java.util.function.Function<Long, CreateNotificationInternalRequest> requestBuilder) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return;
        }
        for (Long recipientId : recipientIds) {
            if (recipientId != null) {
                try {
                    CreateNotificationInternalRequest req = requestBuilder.apply(recipientId);
                    if (req != null) {
                        notificationClient.sendNotification(internalServiceToken, req);
                        log.info("Dispatched broadcast notification '{}' to recipientId={}", req.getType(), recipientId);
                    }
                } catch (Exception e) {
                    log.warn("Non-blocking failure: Unable to send notification to recipientId={}: {}", recipientId, e.getMessage());
                }
            }
        }
    }
}
