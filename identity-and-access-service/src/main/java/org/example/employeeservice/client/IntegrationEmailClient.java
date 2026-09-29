package org.example.employeeservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationEmailClient {

    private final RestTemplate restTemplate;

    @Value("${app.reporting-service.url:${REPORTING_SERVICE_URL:http://reporting-and-integration-service:8083}}")
    private String reportingServiceUrl;

    /**
     * Gửi yêu cầu phát email xác thực chứa mã OTP sang Reporting Service.
     * Bọc try-catch để sự cố mạng không làm phá vỡ transaction tạo tài khoản (Graceful Degradation).
     */
    public void sendActivationEmail(String email, String fullName, String activationKey, Integer expiresInMinutes) {
        String url = reportingServiceUrl + "/api/integration/emails/account-activation";
        log.info("Bắn yêu cầu gửi email kích hoạt tới Reporting Service: {}, email: {}", url, email);

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("fullName", fullName);
        payload.put("activationKey", activationKey);
        payload.put("expiresInMinutes", expiresInMinutes != null ? expiresInMinutes : 15);
        payload.put("idempotencyKey", "ACTIVATE_" + email + "_" + System.currentTimeMillis());

        try {
            restTemplate.postForEntity(url, payload, Object.class);
            log.info("Gửi yêu cầu email kích hoạt sang Reporting Service thành công cho email: {}", email);
        } catch (Exception ex) {
            log.warn("Không thể kết nối sang Reporting Service ({}) để gửi email kích hoạt cho {}. Lỗi: {}",
                    url, email, ex.getMessage());
            // Graceful degradation: Không ném Exception để transaction tạo tài khoản vẫn được commit an toàn
        }
    }
}
