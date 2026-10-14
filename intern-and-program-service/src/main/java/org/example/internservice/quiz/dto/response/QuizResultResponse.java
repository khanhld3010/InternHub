package org.example.internservice.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.quiz.entity.enums.SubmissionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResultResponse {

    private Long submissionId;
    private Long assignmentId;
    private Long quizId;
    private String quizTitle;
    private Integer attemptNumber;
    private Integer maxAttempts;
    private Integer score;
    private Integer totalPoints;
    private BigDecimal percentageScore;
    private Integer passingScore;
    private Boolean passed;
    private SubmissionStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;

    @Builder.Default
    private List<QuizAnswerResultResponse> answerResults = new ArrayList<>();
}
