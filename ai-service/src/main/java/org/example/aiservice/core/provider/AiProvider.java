package org.example.aiservice.core.provider;

import org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse;
import org.example.aiservice.modules.quiz.enums.QuizDifficulty;
import org.example.aiservice.modules.screening.dto.response.ScreeningResultDto;

public interface AiProvider {

    ScreeningResultDto evaluateCandidate(
            String cvText,
            String candidateInfoJson,
            String programCriteriaJson,
            String rulesJson
    );

    AiQuizGenerateResponse generateQuizQuestions(
            String contextText,
            String topic,
            String programName,
            String groupName,
            int questionCount,
            QuizDifficulty difficulty
    );
}
