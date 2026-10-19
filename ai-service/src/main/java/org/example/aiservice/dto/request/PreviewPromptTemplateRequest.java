package org.example.aiservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewPromptTemplateRequest {

    @NotBlank(message = "Nội dung template không được để trống")
    private String templateContent;

    private Map<String, Object> sampleVariables;
}
