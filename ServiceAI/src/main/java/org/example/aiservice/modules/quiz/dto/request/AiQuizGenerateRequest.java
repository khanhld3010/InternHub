package org.example.aiservice.modules.quiz.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.aiservice.modules.quiz.enums.QuizDifficulty;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQuizGenerateRequest {

    private String topic;
    private String contextText; // Nội dung tổng hợp từ báo cáo tuần (nếu có)
    private String programName;
    private String groupName;

    @Min(value = 3, message = "Số lượng câu hỏi tối thiểu là 3 câu")
    @Max(value = 30, message = "Số lượng câu hỏi tối đa là 30 câu")
    @Builder.Default
    private Integer questionCount = 10;

    @Builder.Default
    private QuizDifficulty difficulty = QuizDifficulty.MEDIUM;
}
