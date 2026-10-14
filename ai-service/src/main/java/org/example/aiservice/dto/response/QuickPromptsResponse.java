package org.example.aiservice.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickPromptsResponse {
    private Boolean isActive;
    private String botName;
    private List<String> quickPrompts;
}
