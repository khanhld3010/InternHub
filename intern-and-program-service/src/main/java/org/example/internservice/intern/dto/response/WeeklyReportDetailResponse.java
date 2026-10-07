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
public class WeeklyReportDetailResponse {

    private Long id;
    private String internCode;
    private Long mentorId;
    private String mentorName;
    private Integer weekNumber;
    private LocalDate reportDate;
    private String status;
    private String statusDisplayName;

    // 4 Trụ cột cốt lõi
    private String completedTasksSummary;
    private String unfinishedTasksSummary;
    private String difficultiesAndChallenges;
    private String learningsAndKnowledge;

    private String nextWeekPlan;
    private String reportAttachmentUrl;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<ReportTaskItemResponse> tasks = new ArrayList<>();

    private MentorAssessmentSummary mentorAssessment;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTaskItemResponse {
        private Long id;
        private Long missionItemId;
        private String taskTitle;
        private String taskStatus;
        private String submissionUrl;
        private String note;
        private Boolean isCompleted;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentorAssessmentSummary {
        private Long id;
        private Long mentorId;
        private String mentorName;
        private Integer technicalScore;
        private Integer attitudeScore;
        private Integer teamworkScore;
        private Integer productivityScore;
        private BigDecimal averageScore;
        private String feedback;
        private String nextWeekGoals;
        private LocalDateTime publishedAt;
    }
}
