package org.example.aiservice.modules.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiGraderResponseDto {

    private String aiSummary;
    private String strengthsSummary;
    private String weaknessesSummary;
    private List<AnswerEvaluationItem> answers;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnswerEvaluationItem {
        private Long questionId;
        private BigDecimal score;
        private BigDecimal technicalAccuracyScore;
        private BigDecimal optimizationScore;
        private BigDecimal articulationScore;
        private BigDecimal edgeCasesScore;
        private String evidenceQuote;
        private String rationale;
        private String suggestedSolution;

        @JsonProperty("isFlaggedSuspicious")
        private Boolean isFlaggedSuspicious;

        private String securityNote;
    }
}
