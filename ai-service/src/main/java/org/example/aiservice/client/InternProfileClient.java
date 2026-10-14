package org.example.aiservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Client gọi intern-and-program-service lấy thông tin cơ bản của TTS.
 * Tuân thủ bảo mật:
 * - Forward JWT gốc của TTS trong header Authorization
 * - Chỉ lấy và trả về các trường tối thiểu: tên gọi, chương trình, tên Mentor (Data Minimization)
 */
@Component
@Slf4j
public class InternProfileClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String internServiceUrl;

    public InternProfileClient(
            @Value("${ai.intern-service.url:http://intern-and-program-service:8082}") String internServiceUrl,
            ObjectMapper objectMapper
    ) {
        this.internServiceUrl = internServiceUrl.endsWith("/") ? internServiceUrl.substring(0, internServiceUrl.length() - 1) : internServiceUrl;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public Map<String, String> fetchBasicContext(String jwtToken) {
        Map<String, String> context = new HashMap<>();
        if (jwtToken == null || jwtToken.isBlank()) {
            return context;
        }

        try {
            String bearer = jwtToken.startsWith("Bearer ") ? jwtToken : "Bearer " + jwtToken;
            // Gọi endpoint lấy profile TTS của chính user hiện tại
            String response = restClient.get()
                    .uri(internServiceUrl + "/api/interns/profile/me")
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .retrieve()
                    .body(String.class);

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode data = root.path("data");
                if (!data.isMissingNode()) {
                    String fullName = data.path("fullName").asText("");
                    if (!fullName.isBlank()) {
                        // Trích xuất tên gọi ngắn gọn
                        String[] parts = fullName.trim().split("\\s+");
                        context.put("internShortName", parts[parts.length - 1]);
                    }
                    String program = data.path("programTitle").asText("");
                    if (!program.isBlank()) {
                        context.put("programTitle", program);
                    }
                    String mentor = data.path("mentorName").asText("");
                    if (!mentor.isBlank()) {
                        context.put("mentorName", mentor);
                    }
                }
            }
        } catch (Exception e) {
            // Trường hợp TTS chưa có hồ sơ hoặc service tạm thời không phản hồi
            log.debug("Khong the lay context tu intern-and-program-service: {}", e.getMessage());
        }

        return context;
    }
}
