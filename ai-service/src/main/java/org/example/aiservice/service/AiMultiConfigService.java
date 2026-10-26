package org.example.aiservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.client.GeminiApiClient;
import org.example.aiservice.dto.request.TestProfileRequest;
import org.example.aiservice.dto.request.UpdateFeatureProfileRequest;
import org.example.aiservice.dto.request.UpdateGlobalKeyRequest;
import org.example.aiservice.dto.response.AiMultiConfigResponse;
import org.example.aiservice.dto.response.TestProfileResponse;
import org.example.aiservice.entity.AiFeatureProfile;
import org.example.aiservice.entity.AiGlobalConfig;
import org.example.aiservice.repository.AiFeatureProfileRepository;
import org.example.aiservice.repository.AiGlobalConfigRepository;
import org.example.aiservice.security.AesGcmEncryptionUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiMultiConfigService {

    private final AiGlobalConfigRepository globalConfigRepository;
    private final AiFeatureProfileRepository profileRepository;
    private final AesGcmEncryptionUtil encryptionUtil;
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.encryption.current-version:1}")
    private int currentKeyVersion;

    private static final Map<String, ProfilePreset> DEFAULT_PRESETS = Map.of(
            "CHATBOT", new ProfilePreset("Trợ Lý FAQ Thực Tập", "Hỗ trợ giải đáp nội quy, tài liệu onboarding cho thực tập sinh.", "gemini-3.5-flash-lite", new BigDecimal("0.30"), 1024, "Bạn là trợ lý ảo hỗ trợ thực tập sinh tại InternHub. Trả lời thân thiện, súc tích và chính xác dựa trên quy định công ty."),
            "CV_SCREENING", new ProfilePreset("Sàng Lọc Hồ Sơ & CV", "Trích xuất kỹ năng, so khớp thang đo rubric tuyển dụng, xuất JSON 100% chuẩn xác.", "gemini-3.5-flash-lite", new BigDecimal("0.00"), 2048, "Bạn là chuyên gia thẩm định hồ sơ tuyển dụng. Phân tích bám sát tiêu chí rubric, không thêm thắt suy diễn."),
            "INTERVIEW_GENERATOR", new ProfilePreset("Sinh Bộ Câu Hỏi Phỏng Vấn", "Tạo bộ câu hỏi phỏng vấn kỹ thuật và tình huống đa dạng, sáng tạo, chống lặp lại.", "gemini-3.5-flash-lite", new BigDecimal("0.65"), 3072, "Bạn là chuyên gia phỏng vấn kỹ thuật công nghệ cao. Tạo câu hỏi phân loại sâu sắc, tình huống thực tế."),
            "REPORT_ASSISTANT", new ProfilePreset("Trợ Lý Đánh Giá Báo Cáo Tuần", "Đối soát báo cáo với tiến độ Kanban, phân tích 3 dòng và gợi ý điểm 5 sao khách quan.", "gemini-3.5-flash-lite", new BigDecimal("0.10"), 2048, "BẠN LÀ MỘT TRỢ LÝ GIẢNG VIÊN VÀ CHUYÊN GIA ĐỐI SOÁT THỰC TẬP CÔNG NGHỆ THÔNG TIN CẤP CAO. Nhiệm vụ của bạn là đọc báo cáo tuần của Thực tập sinh, đối chiếu với danh sách các nhiệm vụ trên bảng Kanban của tuần đó, và cung cấp bản phân tích cô đọng 3 dòng cùng đề xuất điểm số (THANG ĐIỂM TỪ 1 ĐẾN 5 SAO).")
    );

    private record ProfilePreset(String name, String desc, String model, BigDecimal temp, int tokens, String sysInstruction) {}

    @Transactional
    public AiMultiConfigResponse getMultiConfig() {
        AiGlobalConfig global = getOrCreateGlobalConfig();
        List<AiFeatureProfile> profiles = getAllProfilesWithDefaults();

        boolean hasKey = global.getApiKeyEncrypted() != null && !global.getApiKeyEncrypted().isBlank();
        String maskedKey = (hasKey && global.getApiKeyLast4() != null) ? "••••••••" + global.getApiKeyLast4() : null;

        AiMultiConfigResponse.GlobalConfigDto globalDto = AiMultiConfigResponse.GlobalConfigDto.builder()
                .provider(global.getProvider())
                .apiKeyLast4(global.getApiKeyLast4())
                .maskedApiKey(maskedKey)
                .hasApiKey(hasKey)
                .isActive(Boolean.TRUE.equals(global.getIsActive()))
                .updatedAt(global.getUpdatedAt())
                .build();

        List<AiMultiConfigResponse.FeatureProfileDto> profileDtos = profiles.stream()
                .map(p -> AiMultiConfigResponse.FeatureProfileDto.builder()
                        .featureKey(p.getFeatureKey())
                        .featureName(p.getFeatureName())
                        .description(p.getDescription())
                        .modelName(p.getModelName())
                        .temperature(p.getTemperature())
                        .maxTokens(p.getMaxTokens())
                        .rateLimitPerMinute(p.getRateLimitPerMinute())
                        .dailyQuota(p.getDailyQuota())
                        .systemInstruction(p.getSystemInstruction())
                        .quickPrompts(parseQuickPrompts(p.getQuickPromptsJson()))
                        .isActive(Boolean.TRUE.equals(p.getIsActive()))
                        .updatedAt(p.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        return AiMultiConfigResponse.builder()
                .globalConfig(globalDto)
                .profiles(profileDtos)
                .build();
    }

    @Transactional
    public void updateGlobalKey(UpdateGlobalKeyRequest request, String adminUserId) {
        AiGlobalConfig global = getOrCreateGlobalConfig();
        String cleanKey = request.getApiKey().trim();
        String last4 = cleanKey.length() >= 4 ? cleanKey.substring(cleanKey.length() - 4) : cleanKey;

        try {
            String encrypted = encryptionUtil.encrypt(cleanKey, currentKeyVersion);
            global.setApiKeyEncrypted(encrypted);
            global.setApiKeyLast4(last4);
            global.setKeyVersion(currentKeyVersion);
            global.setUpdatedBy(adminUserId);
            globalConfigRepository.save(global);
            log.info("[AiMultiConfigService] Admin {} cập nhật thành công Global API Key (*{})", adminUserId, last4);
        } catch (Exception e) {
            log.error("[AiMultiConfigService] Lỗi mã hóa API Key: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi mã hóa API Key hệ thống.");
        }
    }

    @Transactional
    public AiMultiConfigResponse.FeatureProfileDto updateProfile(String featureKey, UpdateFeatureProfileRequest request, String adminUserId) {
        AiFeatureProfile profile = getProfileOrDefault(featureKey);

        profile.setModelName(request.getModelName().trim());
        profile.setTemperature(request.getTemperature());
        profile.setMaxTokens(request.getMaxTokens());
        if (request.getRateLimitPerMinute() != null) {
            profile.setRateLimitPerMinute(request.getRateLimitPerMinute());
        }
        if (request.getDailyQuota() != null) {
            profile.setDailyQuota(request.getDailyQuota());
        }
        profile.setSystemInstruction(request.getSystemInstruction());
        profile.setIsActive(request.getIsActive());
        profile.setUpdatedBy(adminUserId);

        if (request.getQuickPrompts() != null) {
            try {
                profile.setQuickPromptsJson(objectMapper.writeValueAsString(request.getQuickPrompts()));
            } catch (JsonProcessingException e) {
                log.warn("[AiMultiConfigService] Lỗi serialize quickPrompts: {}", e.getMessage());
            }
        }

        profileRepository.save(profile);
        log.info("[AiMultiConfigService] Admin {} cập nhật Profile [{}]: model={}, temp={}, rateLimit={}/m, quota={}/d, active={}",
                adminUserId, featureKey, profile.getModelName(), profile.getTemperature(),
                profile.getRateLimitPerMinute(), profile.getDailyQuota(), profile.getIsActive());

        return AiMultiConfigResponse.FeatureProfileDto.builder()
                .featureKey(profile.getFeatureKey())
                .featureName(profile.getFeatureName())
                .description(profile.getDescription())
                .modelName(profile.getModelName())
                .temperature(profile.getTemperature())
                .maxTokens(profile.getMaxTokens())
                .rateLimitPerMinute(profile.getRateLimitPerMinute())
                .dailyQuota(profile.getDailyQuota())
                .systemInstruction(profile.getSystemInstruction())
                .quickPrompts(parseQuickPrompts(profile.getQuickPromptsJson()))
                .isActive(Boolean.TRUE.equals(profile.getIsActive()))
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    @Transactional
    public TestProfileResponse testProfile(String featureKey, TestProfileRequest request) {
        AiFeatureProfile profile = getProfileOrDefault(featureKey);
        if (!Boolean.TRUE.equals(profile.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tính năng [" + profile.getFeatureName() + "] đang bị tắt bởi Quản trị viên.");
        }

        AiGlobalConfig global = getOrCreateGlobalConfig();
        if (global.getApiKeyEncrypted() == null || global.getApiKeyEncrypted().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hệ thống chưa được thiết lập API Key toàn cầu.");
        }

        String rawKey;
        try {
            rawKey = encryptionUtil.decrypt(global.getApiKeyEncrypted(), global.getKeyVersion());
        } catch (Exception e) {
            log.error("[AiMultiConfigService] Lỗi giải mã API Key: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi giải mã API Key.");
        }

        long start = System.currentTimeMillis();
        try {
            String output = geminiApiClient.generateContent(
                    rawKey,
                    profile.getModelName(),
                    profile.getSystemInstruction(),
                    Collections.emptyList(),
                    request.getTestPrompt(),
                    profile.getTemperature(),
                    Math.min(profile.getMaxTokens(), 1024)
            );
            long latency = System.currentTimeMillis() - start;

            return TestProfileResponse.builder()
                    .success(true)
                    .featureKey(featureKey)
                    .modelName(profile.getModelName())
                    .temperature(profile.getTemperature())
                    .latencyMs(latency)
                    .output(output)
                    .build();

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("[AiMultiConfigService] Lỗi khi chạy test profile {}: {}", featureKey, e.getMessage());
            return TestProfileResponse.builder()
                    .success(false)
                    .featureKey(featureKey)
                    .modelName(profile.getModelName())
                    .temperature(profile.getTemperature())
                    .latencyMs(latency)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Transactional
    public AiFeatureProfile getProfileOrDefault(String featureKey) {
        return profileRepository.findByFeatureKey(featureKey)
                .orElseGet(() -> {
                    ProfilePreset preset = DEFAULT_PRESETS.getOrDefault(featureKey,
                            new ProfilePreset(featureKey, "Mô tả tính năng " + featureKey, "gemini-3.5-flash-lite", new BigDecimal("0.20"), 1024, ""));
                    AiFeatureProfile newProfile = AiFeatureProfile.builder()
                            .featureKey(featureKey)
                            .featureName(preset.name)
                            .description(preset.desc)
                            .modelName(preset.model)
                            .temperature(preset.temp)
                            .maxTokens(preset.tokens)
                            .systemInstruction(preset.sysInstruction)
                            .quickPromptsJson("[]")
                            .isActive(true)
                            .build();
                    return profileRepository.save(newProfile);
                });
    }

    @Transactional
    public AiGlobalConfig getOrCreateGlobalConfig() {
        return globalConfigRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    AiGlobalConfig config = AiGlobalConfig.builder()
                            .provider("GEMINI")
                            .isActive(true)
                            .keyVersion(currentKeyVersion)
                            .build();
                    return globalConfigRepository.save(config);
                });
    }

    private List<AiFeatureProfile> getAllProfilesWithDefaults() {
        List<AiFeatureProfile> existing = profileRepository.findAll();
        Set<String> existingKeys = existing.stream().map(AiFeatureProfile::getFeatureKey).collect(Collectors.toSet());

        List<AiFeatureProfile> result = new ArrayList<>();
        for (AiFeatureProfile p : existing) {
            // Tự động phát hiện và phục hồi tiếng Việt nếu tên bị lỗi mã hóa Mojibake (chứa Ã, Â, ¡, ©, »)
            if (p.getFeatureName() != null && (p.getFeatureName().contains("Ã") || p.getFeatureName().contains("Â") || p.getFeatureName().contains("»") || p.getFeatureName().contains("¡"))) {
                ProfilePreset preset = DEFAULT_PRESETS.get(p.getFeatureKey());
                if (preset != null) {
                    p.setFeatureName(preset.name());
                    if (p.getDescription() != null && (p.getDescription().contains("Ã") || p.getDescription().contains("»"))) {
                        p.setDescription(preset.desc());
                    }
                    if (p.getSystemInstruction() != null && (p.getSystemInstruction().contains("Ã") || p.getSystemInstruction().contains("»"))) {
                        p.setSystemInstruction(preset.sysInstruction());
                    }
                    profileRepository.save(p);
                }
            }
            result.add(p);
        }

        for (String key : DEFAULT_PRESETS.keySet()) {
            if (!existingKeys.contains(key)) {
                result.add(getProfileOrDefault(key));
            }
        }
        result.sort(Comparator.comparing(AiFeatureProfile::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private List<String> parseQuickPrompts(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
