package org.example.aiservice.modules.interview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.aiservice.modules.interview.enums.QuestionCategory;
import org.example.aiservice.modules.interview.enums.QuestionDifficulty;
import org.example.aiservice.modules.interview.enums.QuestionScope;
import org.example.aiservice.modules.interview.enums.QuestionSetStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestionSetResponse {

    private Long id;
    private String title;
    private Long programId;
    private Long internId;
    private QuestionScope scope;
    private String targetRole;
    private QuestionSetStatus status;
    private Integer totalQuestions;
    private Long screeningResultId;
    private BigDecimal screeningMatchScore;
    private String createdByUserId;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<InterviewQuestionResponse> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterviewQuestionResponse {
        private Long id;
        private QuestionCategory category;
        private QuestionDifficulty difficulty;
        private String questionText;
        private String expectedAnswer;
        private String evaluationCriteria;
        private Integer suggestedScoreWeight;
        private Integer displayOrder;
        private String focusArea;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
