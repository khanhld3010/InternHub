package org.example.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.entity.AiChatLog;
import org.example.aiservice.entity.AiSystemConfig;
import org.example.aiservice.repository.AiChatLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAuditLogService {

    private final AiChatLogRepository chatLogRepository;

    /**
     * Ghi audit log bất đồng bộ bằng thread pool riêng của Spring TaskExecutor,
     * giúp trả HTTP response ngay lập tức cho client.
     */
    @Async
    public void saveChatAuditLogAsync(String userId, String sessionId, String question, String answer, AiSystemConfig config, String status, int latencyMs) {
        try {
            AiChatLog logEntity = AiChatLog.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .question(question)
                    .answer(answer)
                    .provider(config != null ? config.getProvider() : "UNKNOWN")
                    .modelName(config != null ? config.getModelName() : "UNKNOWN")
                    .status(status)
                    .latencyMs(latencyMs)
                    .build();
            chatLogRepository.save(logEntity);
        } catch (Exception e) {
            log.warn("Loi ghi log ai_chat_logs bat dong bo: {}", e.getMessage());
        }
    }
}
