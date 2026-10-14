package org.example.aiservice.modules.screening.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.aiservice.common.entity.BaseEntity;
import org.example.aiservice.modules.screening.enums.AiRecommendation;
import org.example.aiservice.modules.screening.enums.EvaluationStatus;

@Entity
@Table(name = "intern_ai_evaluations", indexes = {
        @Index(name = "idx_ai_eval_intern", columnList = "intern_id"),
        @Index(name = "idx_ai_eval_program", columnList = "program_id"),
        @Index(name = "idx_ai_eval_hash", columnList = "cv_file_hash")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternAiEvaluation extends BaseEntity {

    @Column(name = "intern_id", nullable = false)
    private Long internId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "cv_file_hash", length = 64)
    private String cvFileHash;

    @Column(name = "overall_score", nullable = false)
    private Integer overallScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false, length = 30)
    private AiRecommendation recommendation;

    @Column(name = "match_summary", columnDefinition = "TEXT")
    private String matchSummary;

    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    @Column(name = "weaknesses_json", columnDefinition = "TEXT")
    private String weaknessesJson;

    @Column(name = "skills_matched_json", columnDefinition = "TEXT")
    private String skillsMatchedJson;

    @Column(name = "draft_rejection_reason", columnDefinition = "TEXT")
    private String draftRejectionReason;

    @Column(name = "suggested_interview_questions_json", columnDefinition = "TEXT")
    private String suggestedInterviewQuestionsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EvaluationStatus status = EvaluationStatus.SUCCESS;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
