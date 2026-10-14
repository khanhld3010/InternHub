package org.example.internservice.quiz.client.dto;

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
public class AiQuizGenerateClientRequest {

    private String topic;
    private String contextText;
    private String programName;
    private String groupName;
    private Integer questionCount;
    private QuizDifficulty difficulty;
}
