package org.example.aiservice.modules.interview.dto.response;

import lombok.*;
import org.example.aiservice.modules.interview.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationDossierResponse {

    private Long evaluationId;
    private Long sessionId;
    private Integer version;
    private Boolean isActive;
    private String candidateName;
    private String candidateEmail;
    private String scheduleTitle;
    private GradingStatus gradingStatus;
    private BigDecimal totalScore;
    private BigDecimal hrFinalTotalScore;
    private AiRecommendation aiRecommendation;
    private ProctoringStatus proctoringStatus;
    private String aiSummary;
    private String strengthsSummary;
    private String weaknessesSummary;
    private String modelName;
    private String promptVersion;
    private String regradeReason;
    private Integer retryCount;
    private String errorMessage;

    private AntiCheatInfo antiCheatInfo;
    private HrReviewInfo hrReview;
    private List<AnswerEvaluationDetail> answers;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AntiCheatInfo {
        private Integer blurCount;
        private Integer pasteCount;
        private Boolean isViolated;
        private ProctoringStatus proctoringStatus;
        private String details;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HrReviewInfo {
        private HrEvaluationStatus status;
        private BigDecimal hrFinalTotalScore;
        private String notes;
        private String reviewedBy;
        private LocalDateTime reviewedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnswerEvaluationDetail {
        private Long questionId;
        private String questionText;
        private QuestionCategory category;
        private BigDecimal scoreWeight;
        private String candidateAnswer;
        private BigDecimal score;
        private BigDecimal hrOverrideScore;
        private String hrNotes;
        private RubricScores rubric;
        private String evidenceQuote;
        private String rationale;
        private String suggestedSolution;
        private Boolean isFlaggedSuspicious;
        private String securityNote;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RubricScores {
        private BigDecimal technicalAccuracy;
        private BigDecimal optimization;
        private BigDecimal articulation;
        private BigDecimal edgeCases;
    }
}
