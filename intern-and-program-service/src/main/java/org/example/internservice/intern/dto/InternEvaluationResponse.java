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
public class InternEvaluationResponse {
    private Long id;
    private String internCode;
    private Long mentorId;
    private String mentorName;
    private String evaluationType;

    private BigDecimal technicalScore;
    private String technicalComments;

    private BigDecimal attitudeScore;
    private String attitudeComments;

    private BigDecimal softSkillsScore;
    private BigDecimal finalScore;
    private BigDecimal weeklyAssessmentAvgScore;

    private String strengths;
    private String areasForImprovement;

    private String recommendation;
    private String recommendationNote;

    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private String hrComments;
    private String internshipResult;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
