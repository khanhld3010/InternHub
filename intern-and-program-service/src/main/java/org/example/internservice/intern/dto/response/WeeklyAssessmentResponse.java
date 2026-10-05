package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.intern.entity.InternWeeklyAssessment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyAssessmentResponse {

    private Long id;
    private String internCode;
    private Long mentorId;
    private String mentorName;
    private Integer weekNumber;
    private LocalDate assessmentDate;
    private Integer technicalScore;
    private Integer attitudeScore;
    private Integer teamworkScore;
    private Integer productivityScore;
    private BigDecimal averageScore;
    private String feedback;
    private String nextWeekGoals;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WeeklyAssessmentResponse fromEntity(InternWeeklyAssessment entity) {
        if (entity == null) {
            return null;
        }
        return WeeklyAssessmentResponse.builder()
                .id(entity.getId())
                .internCode(entity.getInternCode())
                .mentorId(entity.getMentorId())
                .mentorName(entity.getMentorName())
                .weekNumber(entity.getWeekNumber())
                .assessmentDate(entity.getAssessmentDate())
                .technicalScore(entity.getTechnicalScore())
                .attitudeScore(entity.getAttitudeScore())
                .teamworkScore(entity.getTeamworkScore())
                .productivityScore(entity.getProductivityScore())
                .averageScore(entity.getAverageScore())
                .feedback(entity.getFeedback())
                .nextWeekGoals(entity.getNextWeekGoals())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .publishedAt(entity.getPublishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
