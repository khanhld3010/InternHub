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
    }
}
