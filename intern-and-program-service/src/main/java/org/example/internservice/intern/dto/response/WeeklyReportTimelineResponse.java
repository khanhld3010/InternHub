package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReportTimelineResponse {

    private Integer currentWeek;
    private Integer totalWeeks;

    @Builder.Default
    private List<WeeklyReportItem> reports = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyReportItem {
        private Integer weekNumber;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status;              // NOT_STARTED, DRAFT, SUBMITTED, REVIEWED
        private String statusDisplayName;
        private LocalDateTime submittedAt;
        private BigDecimal mentorAverageScore;
        private String mentorFeedback;
    }
}
