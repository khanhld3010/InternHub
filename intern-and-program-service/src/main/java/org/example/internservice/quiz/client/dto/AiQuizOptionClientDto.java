package org.example.internservice.quiz.client.dto;

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
public class AiQuizOptionClientDto {

    private String optionLabel;
    private String optionText;
    private Boolean isCorrect;
    private Integer orderIndex;
}
