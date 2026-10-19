package org.example.internservice.intern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;

import java.math.BigDecimal;

@Entity
@Table(
        name = "intern_weekly_report_ai_evaluations",
        indexes = {
                @Index(name = "idx_ai_eval_report_active", columnList = "report_id, is_active"),
                @Index(name = "idx_ai_eval_intern_week", columnList = "intern_code, week_number")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternWeeklyReportAiEvaluation extends BaseEntity {

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", insertable = false, updatable = false)
    private InternWeeklyReport report;

    @Column(name = "intern_code", nullable = false, length = 50)
    private String internCode;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Builder.Default
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // --- 3 Dòng Tóm Tắt Cốt Lõi ---
    @Column(name = "progress_summary", nullable = false, columnDefinition = "TEXT")
    private String progressSummary;

    @Column(name = "blockers_and_risks", nullable = false, columnDefinition = "TEXT")
    private String blockersAndRisks;

    @Column(name = "mentor_action_advice", nullable = false, columnDefinition = "TEXT")
    private String mentorActionAdvice;

    // --- Đối Chiếu Kanban ---
    @Enumerated(EnumType.STRING)
    @Column(name = "kanban_alignment_status", nullable = false, length = 30)
    private KanbanAlignmentStatus kanbanAlignmentStatus;

    @Column(name = "kanban_alignment_score", nullable = false)
    private Integer kanbanAlignmentScore;

    @Column(name = "kanban_discrepancy_details", columnDefinition = "TEXT")
    private String kanbanDiscrepancyDetails;

    // --- Gợi Ý Điểm Đánh Giá Chuẩn Thang Điểm 5 (1 - 5 sao) ---
    @Column(name = "suggested_technical_score", nullable = false)
    private Integer suggestedTechnicalScore;

    @Column(name = "suggested_attitude_score", nullable = false)
    private Integer suggestedAttitudeScore;

    @Column(name = "suggested_teamwork_score", nullable = false)
    private Integer suggestedTeamworkScore;

    @Column(name = "suggested_productivity_score", nullable = false)
    private Integer suggestedProductivityScore;

    @Column(name = "suggested_average_score", nullable = false, precision = 3, scale = 1)
    private BigDecimal suggestedAverageScore;

    @Column(name = "suggested_feedback", nullable = false, columnDefinition = "TEXT")
    private String suggestedFeedback;

    @Column(name = "suggested_next_week_goals", columnDefinition = "TEXT")
    private String suggestedNextWeekGoals;

    // --- Metadata Kỹ Thuật ---
    @Builder.Default
    @Column(name = "model_name", nullable = false, length = 50)
    private String modelName = "gemini-1.5-pro";

    @Builder.Default
    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion = "v1.0-weekly-eval-5stars-202610";

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    public enum KanbanAlignmentStatus {
        MATCHED,
        PARTIAL_MISMATCH,
        UNVERIFIED_TASKS,
        NO_TASKS_ASSIGNED
    }
}
