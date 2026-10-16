package org.example.aiservice.core.provider;

import org.example.aiservice.modules.screening.dto.response.ScreeningResultDto;

public interface AiProvider {

    ScreeningResultDto evaluateCandidate(
            String cvText,
            String candidateInfoJson,
            String programCriteriaJson,
            String rulesJson
    );
}
