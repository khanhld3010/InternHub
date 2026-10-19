package org.example.aiservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFeatureProfileRequest {

    @NotBlank(message = "Tên model không được để trống")
    private String modelName;

    @NotNull(message = "Temperature không được để trống")
    @DecimalMin(value = "0.0", message = "Temperature tối thiểu là 0.0")
    @DecimalMax(value = "1.0", message = "Temperature tối đa là 1.0")
    private BigDecimal temperature;

    @NotNull(message = "maxTokens không được để trống")
    private Integer maxTokens;

    private String systemInstruction;

    private List<String> quickPrompts;

    @NotNull(message = "Trạng thái isActive không được để trống")
    private Boolean isActive;
}
