package org.example.internservice.quiz.client.dto;

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
public class AiQuizQuestionClientDto {

    private String questionText;
    private QuestionType questionType;
    private Integer orderIndex;
    private Integer points;
    private String explanation;

    @Builder.Default
    private List<AiQuizOptionClientDto> options = new ArrayList<>();
}
