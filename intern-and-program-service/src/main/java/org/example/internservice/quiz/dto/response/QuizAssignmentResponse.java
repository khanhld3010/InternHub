package org.example.internservice.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.quiz.entity.enums.AssignmentStatus;
import org.example.internservice.quiz.entity.enums.QuizDifficulty;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAssignmentResponse {

    private Long assignmentId;
    private Long quizId;
    private String quizTitle;
    private String topic;
    private QuizDifficulty difficulty;
    private Integer timeLimitMinutes;
    private Integer totalQuestions;
    private Integer totalPoints;
    private Integer passingScore;
    private LocalDate dueDate;
    private Integer maxAttempts;
    private Integer attemptsTaken;
    private Integer highestScore;
    private Boolean passed;
    private AssignmentStatus status;
    private Long latestSubmissionId;
}
