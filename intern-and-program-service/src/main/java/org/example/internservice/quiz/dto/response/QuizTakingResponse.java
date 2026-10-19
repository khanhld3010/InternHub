package org.example.internservice.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizTakingResponse {

    private Long submissionId;
    private Long assignmentId;
    private Long quizId;
    private String quizTitle;
    private String topic;
    private Integer timeLimitMinutes;
    private Integer totalQuestions;
    private Integer totalPoints;
    private Integer passingScore;
    private Integer attemptNumber;
    private Integer maxAttempts;
    private LocalDateTime startedAt;

    @Builder.Default
    private List<QuizTakingQuestionResponse> questions = new ArrayList<>();
}
