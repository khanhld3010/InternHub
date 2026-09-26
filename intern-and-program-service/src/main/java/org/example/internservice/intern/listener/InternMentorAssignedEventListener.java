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
    }
}
