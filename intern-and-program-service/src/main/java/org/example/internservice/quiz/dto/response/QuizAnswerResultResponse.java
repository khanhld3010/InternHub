package org.example.internservice.quiz.dto.response;

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
public class QuizAnswerResultResponse {

    private Long questionId;
    private String questionText;
    private QuestionType questionType;
    private Integer orderIndex;
    private Integer points;
    private Boolean isCorrect;
    private Integer scoreAwarded;
    private String explanation;

    @Builder.Default
    private List<Long> selectedOptionIds = new ArrayList<>();

    @Builder.Default
    private List<Long> correctOptionIds = new ArrayList<>();

    @Builder.Default
    private List<QuizOptionResponse> options = new ArrayList<>();
}
