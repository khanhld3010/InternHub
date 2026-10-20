package org.example.aiservice.modules.interview.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.aiservice.modules.interview.enums.InputMethod;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveCandidateAnswerRequest {

    @jakarta.validation.constraints.Size(max = 20000, message = "Câu trả lời không được vượt quá 20.000 ký tự")
    private String answerText;

    @Builder.Default
    private InputMethod inputMethod = InputMethod.TYPING;

    @NotNull(message = "Base revision is required for optimistic locking")
    private Integer baseRevision;

    private String idempotencyKey;

    @Builder.Default
    private Integer timeSpentSeconds = 0;

    @Builder.Default
    private Boolean isSubmitted = false;
}
