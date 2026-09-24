package org.example.reportingservice.email.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class InternServiceCallbackClient {

    private final RestTemplate restTemplate;

    @Value("${app.intern-service.url:http://intern-and-program-service:8082}")
    private String internServiceUrl;

    public InternServiceCallbackClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void notifyStatusCallback(Long internProfileId, String status, String errorMessage, String idempotencyKey) {
        String callbackUrl = internServiceUrl + "/api/interns/" + internProfileId + "/email-status";
        log.info("Callback cap nhat emailStatus cho internProfileId: {}, status: {}, url: {}", internProfileId, status, callbackUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Call", "true");

            Map<String, Object> body = new HashMap<>();
            body.put("status", status);
            body.put("errorMessage", errorMessage);
            body.put("idempotencyKey", idempotencyKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            restTemplate.exchange(callbackUrl, HttpMethod.PATCH, requestEntity, Void.class);
            log.info("Callback thanh cong toi InternService cho ho so ID: {}", internProfileId);
        } catch (Exception e) {
            log.warn("Loi khi callback toi InternService ({}) cho ho so ID: {}. He thong se dua vao reconciliation: {}",
                    callbackUrl, internProfileId, e.getMessage());
        }
    }
}
