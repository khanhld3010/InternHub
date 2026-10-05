package org.example.internservice.intern.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrEvaluationSummaryItem {
    private Long evaluationId;
    private Long internId;
    private String internCode;
    private String internName;
    private String email;
    private String university;
    private String appliedPosition;
    private Long programId;
    private String programName;
    private Long departmentId;
    private String departmentName;
    private Long mentorId;
    private String mentorName;

    // Trạng thái đánh giá: NOT_STARTED, DRAFT, SUBMITTED, APPROVED
    private String evaluationStatus;

    // Điểm số
    private BigDecimal technicalScore;
    private BigDecimal attitudeScore;
    private BigDecimal softSkillsScore;
    private BigDecimal weeklyAssessmentAvgScore;
    private BigDecimal finalScore;

    private String recommendation;
    private String recommendationNote;
    private String strengths;
    private String areasForImprovement;

    // HR Phê duyệt
    private String hrComments;
    private String hrApprovedBy;
    private LocalDateTime hrApprovedAt;
    private String internshipResult;

    // Trạng thái hồ sơ TTS: INTERNING, COMPLETED, v.v.
    private String internStatus;
}
