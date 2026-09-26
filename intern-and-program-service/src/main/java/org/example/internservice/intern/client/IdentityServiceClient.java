package org.example.internservice.intern.client;

import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class IdentityServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.identity-service.url:http://identity-and-access-service:8081}")
    private String identityServiceUrl;

    public IdentityServiceClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public List<Map<String, Object>> getAllUsers() {
        String url = identityServiceUrl + "/api/users";
        try {
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>>() {}
            );
            if (response.getBody() != null && response.getBody().getData() != null) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("Không thể gọi sang identity-and-access-service để lấy danh sách users: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
