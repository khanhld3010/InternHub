package org.example.aiservice.modules.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQuizOptionDto {

    private String optionLabel; // A, B, C, D
    private String optionText;
    private Boolean isCorrect;
    private Integer orderIndex;
}
