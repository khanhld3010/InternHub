package org.example.internservice.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.quiz.entity.enums.QuizDifficulty;
import org.example.internservice.quiz.entity.enums.QuizStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSummaryResponse {

    private Long id;
    private Long programId;
    private Long mentorId;
    private String title;
    private String topic;
    private QuizDifficulty difficulty;
    private Integer timeLimitMinutes;
    private Integer passingScore;
    private Integer totalQuestions;
    private Integer totalPoints;
    private Integer maxAttempts;
    private QuizStatus status;
    private Integer assignmentCount;
    private LocalDateTime createdAt;
}
