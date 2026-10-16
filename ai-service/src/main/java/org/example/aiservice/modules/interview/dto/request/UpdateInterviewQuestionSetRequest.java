package org.example.aiservice.modules.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.aiservice.modules.interview.enums.QuestionSetStatus;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateInterviewQuestionSetRequest {

    private String title;

    private QuestionSetStatus status;

    private List<QuestionItemDto> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionItemDto {
        private Long id;
        private String questionText;
        private String expectedAnswer;
        private String evaluationCriteria;
        private Integer suggestedScoreWeight;
        private Integer displayOrder;
    }
}
