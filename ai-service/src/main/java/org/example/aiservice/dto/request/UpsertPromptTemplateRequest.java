package org.example.aiservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertPromptTemplateRequest {

    @NotBlank(message = "Tên hiển thị template không được để trống")
    private String templateName;

    @NotBlank(message = "Nội dung template không được để trống")
    private String templateContent;

    private String description;

    private List<String> availableVariables;

    @Builder.Default
    private Boolean createNewVersion = true;
}
