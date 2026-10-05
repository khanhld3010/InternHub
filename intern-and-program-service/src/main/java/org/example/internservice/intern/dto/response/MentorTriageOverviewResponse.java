package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorTriageOverviewResponse {

    private long totalAssigned;
    private long needsWeeklyAssessmentCount;
    private long overdueMidtermCount;
    private BigDecimal groupAverageScore;
    private List<MentorInternItem> interns;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentorInternItem {
        private String internCode;
        private String fullName;
        private String programName;
        private Integer currentWeek;
        private Integer totalWeeks;
        private Integer progressPercent;
        private String weeklyStatus; // NEEDS_ASSESSMENT | OVERDUE_MIDTERM | ASSESSED
        private BigDecimal lastAverageScore;
        private Boolean overdueMidterm;
    }
}
