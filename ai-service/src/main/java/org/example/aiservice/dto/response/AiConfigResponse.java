package org.example.aiservice.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConfigResponse {
    private String provider;
    private String modelName;
    private Boolean hasApiKey;
    private String maskedApiKey;
    private BigDecimal temperature;
    private Integer maxTokens;
    private String systemInstruction;
    private List<String> quickPrompts;
    private Boolean isActive;
}
