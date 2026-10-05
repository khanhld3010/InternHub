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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "intern_weekly_assessments", indexes = {
        @Index(name = "idx_weekly_assess_intern_code", columnList = "intern_code"),
        @Index(name = "idx_weekly_assess_mentor_id", columnList = "mentor_id"),
        @Index(name = "idx_weekly_assess_week_status", columnList = "week_number, status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternWeeklyAssessment extends BaseEntity {

    @Column(name = "intern_code", nullable = false, length = 50)
    private String internCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_code", referencedColumnName = "intern_code", insertable = false, updatable = false)
    private InternProfile internProfile;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "mentor_name", length = 150)
    private String mentorName;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Builder.Default
    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate = LocalDate.now();

    @Column(name = "technical_score", nullable = false)
    private Integer technicalScore;

    @Column(name = "attitude_score", nullable = false)
    private Integer attitudeScore;

    @Column(name = "teamwork_score", nullable = false)
    private Integer teamworkScore;

    @Column(name = "productivity_score", nullable = false)
    private Integer productivityScore;

    @Column(name = "average_score", nullable = false, precision = 3, scale = 1)
    private BigDecimal averageScore;

    @Column(name = "feedback", nullable = false, columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "next_week_goals", columnDefinition = "TEXT")
    private String nextWeekGoals;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssessmentStatus status = AssessmentStatus.DRAFT;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public enum AssessmentStatus {
        DRAFT,
        PUBLISHED
    }
}
