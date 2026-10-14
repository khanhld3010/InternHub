package org.example.internservice.quiz.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.quiz.entity.enums.QuizDifficulty;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateAiQuizRequest {

    @NotNull(message = "Mã chương trình không được để trống")
    private Long programId;

    private Long groupId;
    private Integer weekNumber;
    private String topic;

    @Min(value = 3, message = "Số lượng câu hỏi tối thiểu là 3 câu")
    @Max(value = 30, message = "Số lượng câu hỏi tối đa là 30 câu")
    @Builder.Default
    private Integer questionCount = 10;

    @Builder.Default
    private QuizDifficulty difficulty = QuizDifficulty.MEDIUM;
}
