package org.example.aiservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.dto.request.UpdateAiConfigRequest;
import org.example.aiservice.dto.response.AiConfigResponse;
import org.example.aiservice.entity.AiConfigAudit;
import org.example.aiservice.entity.AiSystemConfig;
import org.example.aiservice.repository.AiConfigAuditRepository;
import org.example.aiservice.repository.AiSystemConfigRepository;
import org.example.aiservice.security.AesGcmEncryptionUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConfigAdminService {

    private final AiSystemConfigRepository configRepository;
    private final AiConfigAuditRepository auditRepository;
    private final AesGcmEncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper;

    @Value("${ai.encryption.current-version:1}")
    private int currentKeyVersion;

    @Transactional(readOnly = true)
    public AiConfigResponse getConfig() {
        AiSystemConfig config = getOrCreateDefaultConfig();

        List<String> quickPrompts = parseQuickPrompts(config.getQuickPromptsJson());
        boolean hasKey = config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isBlank();
        String maskedKey = null;
        if (hasKey && config.getApiKeyLast4() != null) {
            maskedKey = "••••••••" + config.getApiKeyLast4();
        }

        return AiConfigResponse.builder()
                .provider(config.getProvider())
                .modelName(config.getModelName())
                .hasApiKey(hasKey)
                .maskedApiKey(maskedKey)
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .systemInstruction(config.getSystemInstruction())
                .quickPrompts(quickPrompts)
                .isActive(config.getIsActive())
                .build();
    }

    @Transactional
    public AiConfigResponse updateConfig(UpdateAiConfigRequest request, String adminUserId) {
        AiSystemConfig config = getOrCreateDefaultConfig();
        List<String> changedFields = new ArrayList<>();

        if (!config.getProvider().equals(request.getProvider())) {
            changedFields.add("provider");
            config.setProvider(request.getProvider());
        }
        if (!config.getModelName().equals(request.getModelName())) {
            changedFields.add("modelName");
            config.setModelName(request.getModelName());
        }
        if (config.getTemperature().compareTo(request.getTemperature()) != 0) {
            changedFields.add("temperature");
            config.setTemperature(request.getTemperature());
        }
        if (!config.getMaxTokens().equals(request.getMaxTokens())) {
            changedFields.add("maxTokens");
            config.setMaxTokens(request.getMaxTokens());
        }
        if (request.getSystemInstruction() != null && !request.getSystemInstruction().equals(config.getSystemInstruction())) {
            changedFields.add("systemInstruction");
            config.setSystemInstruction(request.getSystemInstruction());
        }
        if (!config.getIsActive().equals(request.getIsActive())) {
            changedFields.add("isActive");
            config.setIsActive(request.getIsActive());
        }

        // Cập nhật Quick Prompts
        String newPromptsJson = null;
        try {
            if (request.getQuickPrompts() != null) {
                newPromptsJson = objectMapper.writeValueAsString(request.getQuickPrompts());
                if (!newPromptsJson.equals(config.getQuickPromptsJson())) {
                    changedFields.add("quickPrompts");
                    config.setQuickPromptsJson(newPromptsJson);
                }
            }
        } catch (Exception e) {
            log.error("Loi parse quick prompts JSON: {}", e.getMessage());
        }

        // Cập nhật API Key (chỉ cập nhật nếu có truyền giá trị mới)
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            String rawKey = request.getApiKey().trim();
            String encrypted = encryptionUtil.encrypt(rawKey, currentKeyVersion);
            config.setApiKeyEncrypted(encrypted);
            config.setKeyVersion(currentKeyVersion);
            config.setApiKeyLast4(rawKey.length() >= 4 ? rawKey.substring(rawKey.length() - 4) : rawKey);
            changedFields.add("apiKey");
        }

        config.setUpdatedBy(adminUserId);
        configRepository.save(config);

        // Ghi Audit trail (tuyệt đối không ghi apiKey vào audit)
        if (!changedFields.isEmpty()) {
            AiConfigAudit audit = AiConfigAudit.builder()
                    .changedBy(adminUserId != null ? adminUserId : "SYSTEM")
                    .changedFields(String.join(",", changedFields))
                    .build();
            auditRepository.save(audit);
        }

        return getConfig();
    }

    public AiSystemConfig getOrCreateDefaultConfig() {
        return configRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            AiSystemConfig def = AiSystemConfig.builder()
                    .provider("GEMINI")
                    .modelName("gemini-1.5-flash")
                    .temperature(new BigDecimal("0.30"))
                    .maxTokens(1024)
                    .systemInstruction("Bạn là Trợ lý ảo AI của nền tảng InternHub...")
                    .quickPromptsJson("[]")
                    .isActive(true)
                    .build();
            return configRepository.save(def);
        });
    }

    private List<String> parseQuickPrompts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
