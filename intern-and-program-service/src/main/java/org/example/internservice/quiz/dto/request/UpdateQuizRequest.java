package org.example.internservice.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.quiz.entity.enums.QuizDifficulty;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuizRequest {

    @NotBlank(message = "Tiêu đề bài Quiz không được để trống")
    private String title;

    private String description;
    private String topic;
    private QuizDifficulty difficulty;
    private Integer timeLimitMinutes;
    private Integer passingScore;
    private Integer maxAttempts;

    @Valid
    @Builder.Default
    private List<CreateQuestionDto> questions = new ArrayList<>();
}
