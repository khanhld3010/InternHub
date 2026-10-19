package org.example.internservice.quiz.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.quiz.entity.enums.QuestionType;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionDto {

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String questionText;

    @Builder.Default
    private QuestionType questionType = QuestionType.SINGLE_CHOICE;

    private Integer orderIndex;

    @Builder.Default
    private Integer points = 10;

    private String explanation;

    @NotEmpty(message = "Câu hỏi phải có ít nhất 2 phương án lựa chọn")
    @Builder.Default
    private List<CreateOptionDto> options = new ArrayList<>();
}
