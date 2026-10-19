package org.example.aiservice.modules.interview.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_answer_evaluations", indexes = {
        @Index(name = "idx_eval_id", columnList = "evaluation_id"),
        @Index(name = "idx_question_id", columnList = "question_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewAnswerEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "hr_override_score", precision = 5, scale = 2)
    private BigDecimal hrOverrideScore;

    @Column(name = "hr_notes", length = 255)
    private String hrNotes;

    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "technical_accuracy_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal technicalAccuracyScore = BigDecimal.ZERO;

    @Column(name = "optimization_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal optimizationScore = BigDecimal.ZERO;

    @Column(name = "articulation_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal articulationScore = BigDecimal.ZERO;

    @Column(name = "edge_cases_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal edgeCasesScore = BigDecimal.ZERO;

    @Column(name = "evidence_quote", nullable = false, columnDefinition = "TEXT")
    private String evidenceQuote;

    @Column(name = "rationale", nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "suggested_solution", nullable = false, columnDefinition = "TEXT")
    private String suggestedSolution;

    @Column(name = "is_flagged_suspicious", nullable = false)
    @Builder.Default
    private Boolean isFlaggedSuspicious = false;

    @Column(name = "security_note", length = 255)
    private String securityNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
