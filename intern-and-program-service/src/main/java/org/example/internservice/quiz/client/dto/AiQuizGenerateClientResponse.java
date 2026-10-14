package org.example.internservice.quiz.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.quiz.entity.enums.QuizDifficulty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQuizGenerateClientResponse {

    private String title;
    private String topic;
    private QuizDifficulty difficulty;
    private Integer totalQuestions;
    private Integer estimatedMinutes;

    @Builder.Default
    private List<AiQuizQuestionClientDto> questions = new ArrayList<>();

    private Boolean fromMock;
    private String errorMessage;
    private LocalDateTime generatedAt;
}
