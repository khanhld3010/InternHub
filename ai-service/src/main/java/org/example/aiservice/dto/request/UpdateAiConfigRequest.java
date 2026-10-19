package org.example.aiservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAiConfigRequest {

    @NotBlank(message = "Provider khong duoc de trong")
    @Pattern(regexp = "^(GEMINI|OPENAI)$", message = "Provider chi ho tro GEMINI hoac OPENAI")
    private String provider;

    @NotBlank(message = "Model name khong duoc de trong")
    @Size(max = 100, message = "Model name toi da 100 ky tu")
    private String modelName;

    @Size(max = 256, message = "API Key toi da 256 ky tu")
    private String apiKey; // null/empty => giu nguyen key cu

    @NotNull(message = "Temperature khong duoc null")
    @DecimalMin(value = "0.0", message = "Temperature toi thieu la 0.0")
    @DecimalMax(value = "1.0", message = "Temperature toi da la 1.0")
    private BigDecimal temperature;

    @NotNull(message = "Max tokens khong duoc null")
    @Min(value = 64, message = "Max tokens toi thieu la 64")
    @Max(value = 2048, message = "Max tokens toi da la 2048")
    private Integer maxTokens;

    @Size(max = 20000, message = "System instruction toi da 20.000 ky tu")
    private String systemInstruction;

    @Size(max = 8, message = "Toi da 8 cau hoi Quick Prompts")
    private List<@NotBlank @Size(max = 200, message = "Moi quick prompt toi da 200 ky tu") String> quickPrompts;

    @NotNull(message = "isActive khong duoc null")
    private Boolean isActive;
}
