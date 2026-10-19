package org.example.aiservice.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptTemplateResponse {

    private Long id;
    private String templateCode;
    private String templateName;
    private String featureGroup;
    private String promptType;
    private Integer version;
    private String templateContent;
    private String description;
    private List<String> availableVariables;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
