package org.example.internservice.program.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCapacityOverviewResponse {

    private CompanySummary companySummary;
    private List<DepartmentCapacityItem> departments;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanySummary {
        private int totalDepartments;
        private int totalActiveInterns;
        private int totalPlannedQuota;
        private int totalActiveMentors;
        private double overallUtilizationRate;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentCapacityItem {
        private Long departmentId;
        private String departmentCode;
        private String departmentName;
        private String description;
        private String leadMentorName;
        private Integer plannedCapacityQuota;
        private int activeInternCount;
        private int activeMentorCount;
        private double utilizationRate;
        private double qualityScoreAvg;
        private int activeProgramsCount;
        private List<MentorRosterItem> mentors;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentorRosterItem {
        private Long mentorId;
        private String mentorName;
        private String email;
        private String avatarUrl;
        private int activeInternCount;
        private String workloadStatus; // AVAILABLE (<3), STANDARD (3-5), OVERLOAD (>5)
    }
}
