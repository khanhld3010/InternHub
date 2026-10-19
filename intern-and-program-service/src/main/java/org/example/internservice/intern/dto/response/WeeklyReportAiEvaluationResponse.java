package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.intern.entity.InternWeeklyReportAiEvaluation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReportAiEvaluationResponse {

    private Long id;
    private Long reportId;
    private String internCode;
    private Integer weekNumber;
    private Integer version;
    private Boolean isActive;

    private String progressSummary;
    private String blockersAndRisks;
    private String mentorActionAdvice;

    private KanbanAlignmentInfo kanbanAlignment;
    private SuggestedAssessmentInfo suggestedAssessment;
    private TechnicalMetadata metadata;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KanbanAlignmentInfo {
        private InternWeeklyReportAiEvaluation.KanbanAlignmentStatus status;
        private Integer score;
        private String details;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedAssessmentInfo {
        private Integer technicalScore;
        private Integer attitudeScore;
        private Integer teamworkScore;
        private Integer productivityScore;
        private BigDecimal averageScore;
        private String feedback;
        private String nextWeekGoals;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TechnicalMetadata {
        private String modelName;
        private String promptVersion;
        private Long executionTimeMs;
        private String requestedBy;
        private LocalDateTime createdAt;
    }
}
