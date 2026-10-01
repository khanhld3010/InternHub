package org.example.employeeservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class NotificationServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.notification-service.url:http://notification-service:8085}")
    private String notificationServiceUrl;

    @Value("${internal.service-token:internhub-internal-secret-token-2026}")
    private String internalServiceToken;

    public NotificationServiceClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Async
    public void dispatchSecurityCommand(String action, Long userId, String reason, String message) {
        String url = notificationServiceUrl + "/api/notifications/internal/security-command";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Token", internalServiceToken);

            Map<String, Object> commandPayload = new HashMap<>();
            commandPayload.put("action", action);
            commandPayload.put("userId", userId);
            commandPayload.put("reason", reason);
            commandPayload.put("message", message);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(commandPayload, headers);
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);
            log.info("Dispatched security command [{}] for userId={} to notification-service", action, userId);
        } catch (Exception e) {
            log.warn("Non-blocking failure: Unable to dispatch security command for userId={}: {}", userId, e.getMessage());
        }
    }
}
