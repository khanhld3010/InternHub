package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorWeeklyReportReviewResponse {

    private String internCode;
    private String internName;
    private String programName;
    private String appliedPosition;
    private Integer weekNumber;
    private LocalDate startDate;
    private LocalDate endDate;

    private WeeklyReportDetailResponse report;
    private WeeklyAssessmentResponse assessment;
}
