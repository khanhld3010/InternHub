package org.example.aiservice.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewPromptTemplateResponse {

    private String resolvedPrompt;
    private List<String> detectedVariables;
}
