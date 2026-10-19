package org.example.aiservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.client.GeminiApiClient;
import org.example.aiservice.client.InternProfileClient;
import org.example.aiservice.dto.request.ChatRequest;
import org.example.aiservice.dto.response.ChatResponse;
import org.example.aiservice.dto.response.QuickPromptsResponse;
import org.example.aiservice.entity.AiChatLog;
import org.example.aiservice.entity.AiSystemConfig;
import org.example.aiservice.repository.AiChatLogRepository;
import org.example.aiservice.security.AesGcmEncryptionUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private final AiConfigAdminService configAdminService;
    private final AiRateLimiterService rateLimiterService;
    private final GeminiApiClient geminiApiClient;
    private final InternProfileClient internProfileClient;
    private final AesGcmEncryptionUtil encryptionUtil;
    private final AiChatLogRepository chatLogRepository;
    private final AiAuditLogService auditLogService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_HISTORY_TURNS = 10;
    private static final long SESSION_TTL_MINUTES = 30;

    public ChatResponse chat(ChatRequest request, String userId, String rawJwtToken) {
        long startTime = System.currentTimeMillis();

        // 1. Kiểm tra rate limit & quota ngày (Fail-closed)
        rateLimiterService.checkAndIncrement(userId);

        // 2. Lấy cấu hình AI đang kích hoạt
        AiSystemConfig config = configAdminService.getOrCreateDefaultConfig();
        if (Boolean.FALSE.equals(config.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Trợ lý ảo hiện đang tạm đóng để bảo trì.");
        }

        if (config.getApiKeyEncrypted() == null || config.getApiKeyEncrypted().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hệ thống AI chưa được cấu hình API Key.");
        }

        // 3. Giải mã API Key
        String rawApiKey;
        try {
            rawApiKey = encryptionUtil.decrypt(config.getApiKeyEncrypted(), config.getKeyVersion());
        } catch (Exception e) {
            log.error("Khong the giai ma API Key AI: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi bảo mật giải mã khóa hệ thống.");
        }

        // 4. Lấy lịch sử hội thoại từ Redis (Key gắn userId để chống IDOR)
        String historyKey = "ai:chat:history:" + userId + ":" + request.getSessionId();
        List<Map<String, String>> history = loadHistoryFromRedis(historyKey);

        // 5. Context Injection (Tối thiểu hóa dữ liệu: tên gọi, chương trình, mentor)
        Map<String, String> internContext = internProfileClient.fetchBasicContext(rawJwtToken);
        String finalSystemInstruction = buildPromptWithContext(config.getSystemInstruction(), internContext);

        // 6. Gọi Gemini API
        String reply;
        String status = "SUCCESS";
        try {
            reply = geminiApiClient.generateContent(
                    rawApiKey,
                    config.getModelName(),
                    finalSystemInstruction,
                    history,
                    request.getMessage(),
                    config.getTemperature(),
                    config.getMaxTokens()
            );
        } catch (Exception e) {
            status = "LLM_ERROR";
            auditLogService.saveChatAuditLogAsync(userId, request.getSessionId(), request.getMessage(), null, config, status, (int) (System.currentTimeMillis() - startTime));
            throw e;
        }

        int latencyMs = (int) (System.currentTimeMillis() - startTime);

        // 7. Cập nhật lịch sử Redis (Lưu 10 lượt gần nhất, TTL 30 phút)
        appendHistoryToRedis(historyKey, history, request.getMessage(), reply);

        // 8. Ghi Audit Log bất đồng bộ (@Async)
        auditLogService.saveChatAuditLogAsync(userId, request.getSessionId(), request.getMessage(), reply, config, status, latencyMs);

        return ChatResponse.builder().reply(reply).build();
    }

    public void clearSession(String sessionId, String userId) {
        String historyKey = "ai:chat:history:" + userId + ":" + sessionId;
        try {
            redisTemplate.delete(historyKey);
        } catch (Exception e) {
            log.warn("Loi khi xoa session Redis: {}", e.getMessage());
        }
    }

    public QuickPromptsResponse getQuickPrompts() {
        AiSystemConfig config = configAdminService.getOrCreateDefaultConfig();
        List<String> prompts = new ArrayList<>();
        if (config.getQuickPromptsJson() != null) {
            try {
                prompts = objectMapper.readValue(config.getQuickPromptsJson(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Loi parse quick prompts: {}", e.getMessage());
            }
        }

        return QuickPromptsResponse.builder()
                .isActive(config.getIsActive())
                .botName("Trợ lý ảo InternHub")
                .quickPrompts(prompts)
                .build();
    }

    /**
     * Dọn dẹp log quá 180 ngày (chạy vào 02:00 sáng mỗi ngày)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldLogsJob() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(180);
            int deleted = chatLogRepository.deleteOldLogs(cutoff);
            log.info("Retention job: Da xoa {} ban ghi chat log cu hon 180 ngay.", deleted);
        } catch (Exception e) {
            log.error("Loi khi chay cleanupOldLogsJob: {}", e.getMessage());
        }
    }

    private List<Map<String, String>> loadHistoryFromRedis(String historyKey) {
        try {
            String json = redisTemplate.opsForValue().get(historyKey);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            }
        } catch (Exception e) {
            log.warn("Loi doc lich su tu Redis: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private void appendHistoryToRedis(String historyKey, List<Map<String, String>> history, String userMsg, String botReply) {
        try {
            history.add(Map.of("role", "user", "text", userMsg));
            history.add(Map.of("role", "model", "text", botReply));

            // Cắt còn tối đa 10 lượt gần nhất (20 messages)
            if (history.size() > MAX_HISTORY_TURNS * 2) {
                history = history.subList(history.size() - (MAX_HISTORY_TURNS * 2), history.size());
            }

            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(historyKey, json, SESSION_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Loi ghi lich su vao Redis: {}", e.getMessage());
        }
    }

    private String buildPromptWithContext(String baseInstruction, Map<String, String> context) {
        StringBuilder sb = new StringBuilder();
        if (baseInstruction != null) {
            sb.append(baseInstruction).append("\n\n");
        }
        if (!context.isEmpty()) {
            sb.append("[THÔNG TIN THỰC TẬP SINH ĐANG HỎI - NỘI BỘ]\n");
            if (context.containsKey("internShortName")) {
                sb.append("- Tên gọi: ").append(context.get("internShortName")).append("\n");
            }
            if (context.containsKey("programTitle")) {
                sb.append("- Chương trình: ").append(context.get("programTitle")).append("\n");
            }
            if (context.containsKey("mentorName")) {
                sb.append("- Mentor phụ trách: ").append(context.get("mentorName")).append("\n");
            }
            sb.append("\nHãy xưng hô thân thiện dựa trên tên gọi trên nếu có.\n");
        }
        return sb.toString();
    }
}
