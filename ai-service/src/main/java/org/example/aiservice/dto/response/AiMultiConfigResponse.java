package org.example.aiservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMultiConfigResponse {

    private GlobalConfigDto globalConfig;
    private List<FeatureProfileDto> profiles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlobalConfigDto {
        private String provider;
        private String apiKeyLast4;
        private String maskedApiKey;
        private boolean hasApiKey;
        private boolean isActive;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureProfileDto {
        private String featureKey;
        private String featureName;
        private String description;
        private String modelName;
        private BigDecimal temperature;
        private Integer maxTokens;
        private Integer rateLimitPerMinute;
        private Integer dailyQuota;
        private String systemInstruction;
        private List<String> quickPrompts;
        private boolean isActive;
        private LocalDateTime updatedAt;
    }
}
