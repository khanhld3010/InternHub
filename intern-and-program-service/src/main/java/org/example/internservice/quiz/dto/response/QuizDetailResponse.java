package org.example.internservice.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.quiz.entity.enums.QuizDifficulty;
import org.example.internservice.quiz.entity.enums.QuizStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizDetailResponse {

    private Long id;
    private Long programId;
    private String programName;
    private Long mentorId;
    private String title;
    private String description;
    private String topic;
    private QuizDifficulty difficulty;
    private Integer timeLimitMinutes;
    private Integer passingScore;
    private Integer totalQuestions;
    private Integer totalPoints;
    private Integer maxAttempts;
    private QuizStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<QuizQuestionResponse> questions = new ArrayList<>();
}
