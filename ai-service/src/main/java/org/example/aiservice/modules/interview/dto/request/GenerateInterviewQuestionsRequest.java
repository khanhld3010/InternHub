package org.example.aiservice.modules.interview.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateInterviewQuestionsRequest {

    @NotNull(message = "Mã chương trình (programId) không được để trống")
    private Long programId;

    private Long internId;

    private String targetRole;

    @Builder.Default
    private Integer totalQuestions = 8;

    @Builder.Default
    private Boolean includeScreeningDossier = true;

    private String customRequirements;
}
