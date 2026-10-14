package org.example.internservice.meeting.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Component
@Slf4j
public class MeetingEmailClient {

    private final RestTemplate restTemplate;

    @Value("${app.reporting-service.url:http://reporting-and-integration-service:8083}")
    private String reportingServiceUrl;

    public MeetingEmailClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Async
    public void sendMeetingEmailAsync(SendMeetingEmailPayload payload) {
        if (payload == null || payload.getInternEmail() == null || payload.getInternEmail().isBlank()) {
            log.warn("Bo qua gui email meeting vi thieu dia chi email");
            return;
        }

        String url = reportingServiceUrl + "/api/integration/emails/meeting-schedule";
        try {
            log.info("Gui REST request den reporting-service de dispatch email meeting ID: {} cho {}",
                    payload.getMeetingId(), payload.getInternEmail());
            ResponseEntity<String> res = restTemplate.postForEntity(url, payload, String.class);
            log.info("Ket qua dispatch email meeting: status={}", res.getStatusCode());
        } catch (Exception e) {
            log.warn("Non-blocking failure: Khong the gui email cuoc hop sang reporting-service: {}", e.getMessage());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMeetingEmailPayload {
        private String idempotencyKey;
        private Long meetingId;
        private String title;
        private String description;
        private String meetingType;
        private String locationOrUrl;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String organizerName;
        private String organizerRole;
        private String internName;
        private String internEmail;
        private String actionType;
        private String provider;
        private Integer sequenceNumber;
    }
}

