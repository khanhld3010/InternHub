package org.example.internservice.intern.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "intern_evaluations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"intern_code", "evaluation_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intern_code", nullable = false, length = 50)
    private String internCode;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "mentor_name", length = 150)
    private String mentorName;

    @Column(name = "evaluation_type", nullable = false, length = 20)
    @Builder.Default
    private String evaluationType = "FINAL"; // 'MIDTERM', 'FINAL'

    // Điểm Kỹ năng chuyên môn (1.0 - 10.0)
    @Column(name = "technical_score", nullable = false, precision = 3, scale = 1)
    private BigDecimal technicalScore;

    @Column(name = "technical_comments", columnDefinition = "TEXT", nullable = false)
    private String technicalComments;

    // Điểm Thái độ & Tác phong (1.0 - 10.0)
    @Column(name = "attitude_score", nullable = false, precision = 3, scale = 1)
    private BigDecimal attitudeScore;

    @Column(name = "attitude_comments", columnDefinition = "TEXT", nullable = false)
    private String attitudeComments;

    // Điểm Kỹ năng mềm & Tinh thần đồng đội (1.0 - 10.0)
    @Column(name = "soft_skills_score", nullable = false, precision = 3, scale = 1)
    private BigDecimal softSkillsScore;

    // Điểm tổng kết chung (1.0 - 10.0)
    @Column(name = "final_score", nullable = false, precision = 3, scale = 1)
    private BigDecimal finalScore;

    // Điểm trung bình tuần tham chiếu (Snapshot từ intern_weekly_assessments)
    @Column(name = "weekly_assessment_avg_score", precision = 3, scale = 1)
    private BigDecimal weeklyAssessmentAvgScore;

    @Column(name = "strengths", columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "areas_for_improvement", columnDefinition = "TEXT")
    private String areasForImprovement;

    // Khuyến nghị: HIRE_FULLTIME, EXTEND_INTERNSHIP, PASS, FAIL
    @Column(name = "recommendation", nullable = false, length = 30)
    private String recommendation;

    @Column(name = "recommendation_note", columnDefinition = "TEXT")
    private String recommendationNote;

    // Trạng thái: DRAFT, SUBMITTED, APPROVED
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 150)
    private String approvedBy;

    // Ý kiến nhận xét chính thức từ Phòng Nhân sự (HR)
    @Column(name = "hr_comments", columnDefinition = "TEXT")
    private String hrComments;

    // Kết luận thực tập: PASSED, EXCELLENT, FAILED
    @Column(name = "internship_result", length = 30)
    @Builder.Default
    private String internshipResult = "PASSED";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
