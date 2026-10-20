package org.example.aiservice.modules.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWeeklyReportEvaluationResult {

    private String progressSummary;
    private String blockersAndRisks;
    private String mentorActionAdvice;

    private String kanbanAlignmentStatus;
    private Integer kanbanAlignmentScore;
    private String kanbanDiscrepancyDetails;

    private Integer suggestedTechnicalScore;
    private Integer suggestedAttitudeScore;
    private Integer suggestedTeamworkScore;
    private Integer suggestedProductivityScore;
    private BigDecimal suggestedAverageScore;

    private String suggestedFeedback;
    private String suggestedNextWeekGoals;

    private String modelName;
    private String promptVersion;
    private Long executionTimeMs;
}
