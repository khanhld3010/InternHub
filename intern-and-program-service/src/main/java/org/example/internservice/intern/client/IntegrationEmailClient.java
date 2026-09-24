package org.example.internservice.intern.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@Slf4j
public class IntegrationEmailClient {

    private final RestTemplate restTemplate;

    @Value("${app.reporting-service.url:http://reporting-and-integration-service:8083}")
    private String reportingServiceUrl;

    public IntegrationEmailClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void sendInternDecisionEmail(Map<String, Object> payload) {
        String url = reportingServiceUrl + "/api/integration/emails/intern-decision";
        log.info("Gui yeu cau sang ReportingService tai URL: {}, payload: {}", url, payload);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Call", "true");

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, requestEntity, Void.class);
            log.info("Gui yeu cau sang ReportingService thanh cong cho internProfileId: {}", payload.get("internProfileId"));
        } catch (Exception e) {
            log.warn("Khong the ket noi toi ReportingService ({}) cho internProfileId: {}. Loi: {}",
                    url, payload.get("internProfileId"), e.getMessage());
            // Khong nem exception de tranh rollback giao dich duyet ho so cua HR
        }
    }
}
