package org.example.internservice.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateQuizRequest {

    @NotNull(message = "Mã chương trình không được để trống")
    private Long programId;

    @NotBlank(message = "Tiêu đề bài Quiz không được để trống")
    private String title;

    private String description;
    private String topic;

    @Builder.Default
    private QuizDifficulty difficulty = QuizDifficulty.MEDIUM;

    @Builder.Default
    private Integer timeLimitMinutes = 15;

    @Builder.Default
    private Integer passingScore = 70;

    @Builder.Default
    private Integer maxAttempts = 1;

    @Valid
    @Builder.Default
    private List<CreateQuestionDto> questions = new ArrayList<>();
}
