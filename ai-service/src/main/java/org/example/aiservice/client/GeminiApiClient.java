package org.example.aiservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Client kết nối Google Gemini API qua Google AI Studio.
 * Tuân thủ bảo mật:
 * - API Key chỉ gửi qua Header 'x-goog-api-key' (tuyệt đối không đưa vào URL ?key=)
 * - Base URL được hardcode hoặc cấu hình an toàn, không mở cho admin cấu hình tùy ý (chống SSRF)
 * - Timeout kết nối 5s, timeout đọc 30s
 * - Lỗi từ provider được che giấu thành 503 chung, không để lộ exception chi tiết
 */
@Component
@Slf4j
public class GeminiApiClient {

    public static final String FALLBACK_FRIENDLY_MESSAGE =
            "Hệ thống AI hiện đang trong thời gian bảo trì hoặc quá tải lưu lượng tạm thời. Vui lòng quay lại sau ít phút hoặc trao đổi trực tiếp với HR/Mentor qua mục Hỗ trợ.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public GeminiApiClient(
            @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${ai.gemini.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${ai.gemini.read-timeout-ms:30000}") int readTimeout,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @CircuitBreaker(name = "geminiClient", fallbackMethod = "fallbackGenerateContent")
    public String generateContent(
            String rawApiKey,
            String modelName,
            String systemInstruction,
            List<Map<String, String>> conversationHistory,
            String currentQuestion,
            BigDecimal temperature,
            int maxTokens
    ) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Service chua duoc cau hinh API Key hop le.");
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();

            // 1. System Instruction
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                ObjectNode sysNode = root.putObject("systemInstruction");
                ArrayNode partsNode = sysNode.putArray("parts");
                partsNode.addObject().put("text", systemInstruction);
            }

            // 2. Generation Config
            ObjectNode genConfig = root.putObject("generationConfig");
            if (temperature != null) {
                genConfig.put("temperature", temperature.doubleValue());
            }
            genConfig.put("maxOutputTokens", maxTokens);

            // 3. Contents (History + Current Question)
            ArrayNode contentsNode = root.putArray("contents");

            if (conversationHistory != null) {
                for (Map<String, String> turn : conversationHistory) {
                    String role = turn.get("role"); // "user" hoặc "model"
                    String text = turn.get("text");
                    if (role != null && text != null) {
                        ObjectNode contentItem = contentsNode.addObject();
                        contentItem.put("role", "assistant".equalsIgnoreCase(role) ? "model" : "user");
                        contentItem.putArray("parts").addObject().put("text", text);
                    }
                }
            }

            // Lượt hỏi hiện tại của TTS
            ObjectNode currentTurn = contentsNode.addObject();
            currentTurn.put("role", "user");
            currentTurn.putArray("parts").addObject().put("text", currentQuestion);

            String requestBodyJson = objectMapper.writeValueAsString(root);
            String url = baseUrl + "/v1beta/models/" + modelName + ":generateContent";

            // Thực hiện gọi REST API với API Key đặt trong HEADER
            byte[] responseBytes = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                    .header("x-goog-api-key", rawApiKey.trim())
                    .body(requestBodyJson)
                    .retrieve()
                    .body(byte[].class);

            if (responseBytes == null || responseBytes.length == 0) {
                log.warn("Gemini tra ve body rong");
                return "Trợ lý ảo tạm thời không sinh được câu trả lời. Vui lòng thử lại sau.";
            }

            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);

            // Parse kết quả trả về
            JsonNode responseJson = objectMapper.readTree(responseBody);
            JsonNode candidates = responseJson.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }

            log.warn("Gemini response khong co candidate text hop le: {}", responseBody);
            return "Trợ lý ảo tạm thời không sinh được câu trả lời. Vui lòng thử lại sau.";

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Loi khi goi Google Gemini API: {}", ex.getMessage());
            // An toan: khong truyen tiep noi dung exception cho client
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Trợ lý ảo tạm thời không khả dụng. Vui lòng thử lại sau hoặc liên hệ HR/Mentor."
            );
        }
    }

    /**
     * Phương thức Fallback khi Circuit Breaker kích hoạt cho generateContent
     */
    public String fallbackGenerateContent(
            String rawApiKey,
            String modelName,
            String systemInstruction,
            List<Map<String, String>> conversationHistory,
            String currentQuestion,
            BigDecimal temperature,
            int maxTokens,
            Throwable throwable
    ) {
        log.warn("Resilience4j Circuit Breaker [geminiClient] kích hoạt fallback cho generateContent. Lý do: {}", throwable.getMessage());
        return FALLBACK_FRIENDLY_MESSAGE;
    }

    /**
     * Gọi Gemini API với Structured JSON Output (responseMimeType: application/json & responseSchema).
     */
    @CircuitBreaker(name = "geminiClient", fallbackMethod = "fallbackGenerateStructuredContent")
    public String generateStructuredContent(
            String rawApiKey,
            String modelName,
            String systemInstruction,
            String promptText,
            JsonNode jsonSchema,
            BigDecimal temperature
    ) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Service chưa được cấu hình API Key.");
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();

            // 1. System Instruction
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                ObjectNode sysNode = root.putObject("systemInstruction");
                ArrayNode partsNode = sysNode.putArray("parts");
                partsNode.addObject().put("text", systemInstruction);
            }

            // 2. Generation Config với JSON Schema
            ObjectNode genConfig = root.putObject("generationConfig");
            genConfig.put("responseMimeType", "application/json");
            if (jsonSchema != null) {
                genConfig.set("responseSchema", jsonSchema);
            }
            if (temperature != null) {
                genConfig.put("temperature", temperature.doubleValue());
            } else {
                genConfig.put("temperature", 0.1); // Điểm đánh giá phỏng vấn cần tính xác định cao
            }

            // 3. Contents
            ArrayNode contentsNode = root.putArray("contents");
            ObjectNode userTurn = contentsNode.addObject();
            userTurn.put("role", "user");
            userTurn.putArray("parts").addObject().put("text", promptText);

            String requestBodyJson = objectMapper.writeValueAsString(root);
            String url = baseUrl + "/v1beta/models/" + modelName + ":generateContent";

            byte[] responseBytes = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                    .header("x-goog-api-key", rawApiKey.trim())
                    .body(requestBodyJson)
                    .retrieve()
                    .body(byte[].class);

            if (responseBytes == null || responseBytes.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini API trả về body rỗng khi chấm bài.");
            }

            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            JsonNode responseJson = objectMapper.readTree(responseBody);
            JsonNode candidates = responseJson.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }

            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini phản hồi không chứa candidate text hợp lệ.");

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Lỗi khi gọi Google Gemini Structured API: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Lỗi kết nối Gemini Grader: " + ex.getMessage());
        }
    }

    /**
     * Phương thức Fallback khi Circuit Breaker kích hoạt cho generateStructuredContent
     */
    public String fallbackGenerateStructuredContent(
            String rawApiKey,
            String modelName,
            String systemInstruction,
            String promptText,
            JsonNode jsonSchema,
            BigDecimal temperature,
            Throwable throwable
    ) {
        log.warn("Resilience4j Circuit Breaker [geminiClient] kích hoạt fallback cho generateStructuredContent. Lý do: {}", throwable.getMessage());
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                FALLBACK_FRIENDLY_MESSAGE
        );
    }

    /**
     * AI-17: Gọi Gemini streamGenerateContent với SSE (Server-Sent Events).
     * Nhận stream chunk và callback consumer từng đoạn text cho client.
     */
    public void streamGenerateContent(
            String rawApiKey,
            String modelName,
            String systemInstruction,
            List<Map<String, String>> conversationHistory,
            String currentQuestion,
            BigDecimal temperature,
            int maxTokens,
            Consumer<String> chunkConsumer
    ) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Service chưa được cấu hình API Key hợp lệ.");
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();

            // 1. System Instruction
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                ObjectNode sysNode = root.putObject("systemInstruction");
                ArrayNode partsNode = sysNode.putArray("parts");
                partsNode.addObject().put("text", systemInstruction);
            }

            // 2. Generation Config
            ObjectNode genConfig = root.putObject("generationConfig");
            if (temperature != null) {
                genConfig.put("temperature", temperature.doubleValue());
            }
            genConfig.put("maxOutputTokens", maxTokens);

            // 3. Contents
            ArrayNode contentsNode = root.putArray("contents");
            if (conversationHistory != null) {
                for (Map<String, String> turn : conversationHistory) {
                    String role = turn.get("role");
                    String text = turn.get("text");
                    if (role != null && text != null) {
                        ObjectNode contentItem = contentsNode.addObject();
                        contentItem.put("role", "assistant".equalsIgnoreCase(role) ? "model" : "user");
                        contentItem.putArray("parts").addObject().put("text", text);
                    }
                }
            }

            ObjectNode currentTurn = contentsNode.addObject();
            currentTurn.put("role", "user");
            currentTurn.putArray("parts").addObject().put("text", currentQuestion);

            String requestBodyJson = objectMapper.writeValueAsString(root);
            String url = baseUrl + "/v1beta/models/" + modelName + ":streamGenerateContent?alt=sse";

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("x-goog-api-key", rawApiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(45))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() >= 400) {
                log.error("Gemini stream API error status: {}", response.statusCode());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Lỗi từ dịch vụ AI (Status " + response.statusCode() + ").");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String dataPayload = line.substring(5).trim();
                        if (dataPayload.isEmpty() || "[DONE]".equalsIgnoreCase(dataPayload)) {
                            continue;
                        }
                        try {
                            JsonNode chunkJson = objectMapper.readTree(dataPayload);
                            JsonNode candidates = chunkJson.path("candidates");
                            if (candidates.isArray() && !candidates.isEmpty()) {
                                JsonNode parts = candidates.get(0).path("content").path("parts");
                                if (parts.isArray() && !parts.isEmpty()) {
                                    String chunkText = parts.get(0).path("text").asText();
                                    if (chunkText != null && !chunkText.isEmpty()) {
                                        chunkConsumer.accept(chunkText);
                                    }
                                }
                            }
                        } catch (Exception parseEx) {
                            log.debug("Skip unparseable stream line: {}", dataPayload);
                        }
                    }
                }
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Lỗi khi thực hiện stream content từ Gemini: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Lỗi kết nối stream trợ lý ảo: " + ex.getMessage());
        }
    }
}

