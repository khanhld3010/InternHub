package org.example.aiservice.modules.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.aiservice.modules.quiz.enums.QuizDifficulty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQuizGenerateResponse {

    private String title;
    private String topic;
    private QuizDifficulty difficulty;
    private Integer totalQuestions;
    private Integer estimatedMinutes;

    @Builder.Default
    private List<AiQuizQuestionDto> questions = new ArrayList<>();

    @Builder.Default
    private Boolean fromMock = false;

    private String errorMessage;
    private LocalDateTime generatedAt;
}
