package org.example.aiservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestProfileResponse {

    private boolean success;
    private String featureKey;
    private String modelName;
    private BigDecimal temperature;
    private long latencyMs;
    private String output;
    private String errorMessage;
}
