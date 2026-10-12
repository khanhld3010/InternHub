package org.example.aiservice.modules.screening.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.aiservice.modules.screening.enums.AiRecommendation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningResultDto {

    private Long internId;
    private Long programId;
    private Integer overallScore;
    private AiRecommendation recommendation;
    private String matchSummary;

    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    @Builder.Default
    private List<String> weaknesses = new ArrayList<>();

    @Builder.Default
    private List<String> skillsMatched = new ArrayList<>();

    private String draftRejectionReason;

    @Builder.Default
    private List<String> suggestedInterviewQuestions = new ArrayList<>();

    @Builder.Default
    private LocalDateTime evaluatedAt = LocalDateTime.now();

    private boolean fromCache;
    private String errorMessage;
}
