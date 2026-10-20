package org.example.aiservice.modules.interview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.aiservice.modules.interview.enums.AiRecommendation;
import org.example.aiservice.modules.interview.enums.GradingStatus;
import org.example.aiservice.modules.interview.enums.HrEvaluationStatus;
import org.example.aiservice.modules.interview.enums.ProctoringStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_evaluations", indexes = {
        @Index(name = "idx_eval_session_id", columnList = "session_id"),
        @Index(name = "idx_eval_session_active", columnList = "session_id, is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "hr_final_total_score", precision = 5, scale = 2)
    private BigDecimal hrFinalTotalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_recommendation", length = 30)
    private AiRecommendation aiRecommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "proctoring_status", nullable = false, length = 30)
    @Builder.Default
    private ProctoringStatus proctoringStatus = ProctoringStatus.CLEAN;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "strengths_summary", columnDefinition = "TEXT")
    private String strengthsSummary;

    @Column(name = "weaknesses_summary", columnDefinition = "TEXT")
    private String weaknessesSummary;

    @Column(name = "anti_cheat_analysis", length = 255)
    private String antiCheatAnalysis;

    @Column(name = "model_name", nullable = false, length = 50)
    @Builder.Default
    private String modelName = "gemini-1.5-pro";

    @Column(name = "prompt_version", length = 50)
    @Builder.Default
    private String promptVersion = "v1.2-rubric-202610";

    @Column(name = "regrade_reason", columnDefinition = "TEXT")
    private String regradeReason;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "hr_notes", columnDefinition = "TEXT")
    private String hrNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "hr_status", nullable = false, length = 30)
    @Builder.Default
    private HrEvaluationStatus hrStatus = HrEvaluationStatus.PENDING_REVIEW;

    @Column(name = "reviewed_by_hr_id", length = 100)
    private String reviewedByHrId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", nullable = false, length = 30)
    @Builder.Default
    private GradingStatus gradingStatus = GradingStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
