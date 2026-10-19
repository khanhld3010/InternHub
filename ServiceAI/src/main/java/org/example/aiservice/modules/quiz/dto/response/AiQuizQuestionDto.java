package org.example.aiservice.modules.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.aiservice.modules.quiz.enums.QuestionType;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQuizQuestionDto {

    private String questionText;
    private QuestionType questionType;
    private Integer orderIndex;
    private Integer points;
    private String explanation;

    @Builder.Default
    private List<AiQuizOptionDto> options = new ArrayList<>();
}
