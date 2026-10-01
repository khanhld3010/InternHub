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

    public Long findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> users = getAllUsers();
            for (Map<String, Object> u : users) {
                Object uEmail = u.get("email");
                Object uUsername = u.get("username");
                if ((uEmail != null && email.equalsIgnoreCase(uEmail.toString())) ||
                    (uUsername != null && email.equalsIgnoreCase(uUsername.toString()))) {
                    Object idVal = u.get("id");
                    if (idVal != null) {
                        return Long.valueOf(idVal.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tra cứu userId theo email '{}': {}", email, e.getMessage());
        }
        return null;
    }

    public List<Long> findUserIdsByRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> users = getAllUsers();
            return users.stream()
                    .filter(u -> {
                        Object role = u.get("role");
                        Object position = u.get("position");
                        return (role != null && roleName.equalsIgnoreCase(role.toString())) ||
                               (position != null && roleName.equalsIgnoreCase(position.toString()));
                    })
                    .map(u -> u.get("id"))
                    .filter(java.util.Objects::nonNull)
                    .map(id -> Long.valueOf(id.toString()))
                    .toList();
        } catch (Exception e) {
            log.warn("Không thể tra cứu userIds theo role '{}': {}", roleName, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> createUserAccount(Map<String, Object> registerRequest) {
        String url = identityServiceUrl + "/api/auth/register";
        log.info("Gọi sang identity-service để tạo tài khoản mới: url={}, username={}", url, registerRequest.get("username"));
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Call", "true");

            org.springframework.http.HttpEntity<Map<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(registerRequest, headers);
            ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {}
            );

            if (response.getBody() != null && response.getBody().getData() != null) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            log.error("Lỗi khi gọi sang identity-service để tạo tài khoản: {}", e.getMessage());
            throw new RuntimeException("Không thể tạo tài khoản người dùng trên Identity Service: " + e.getMessage(), e);
        }
        throw new RuntimeException("Tạo tài khoản không thành công, không nhận được phản hồi hợp lệ");
    }
}
