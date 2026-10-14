package org.example.internservice.quiz.dto.response;

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
public class QuizOptionResponse {

    private Long id;
    private String optionLabel;
    private String optionText;
    private Boolean isCorrect;
    private Integer orderIndex;
}
