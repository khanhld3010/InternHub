package org.example.aiservice.modules.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEvaluateWeeklyReportRequest {

    private String internCode;
    private Integer weekNumber;
    private String completedTasksSummary;
    private String unfinishedTasksSummary;
    private String difficultiesAndChallenges;
    private String learningsAndKnowledge;
    private List<KanbanTaskSnapshot> kanbanTasks;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KanbanTaskSnapshot {
        private Long id;
        private String title;
        private String description;
        private String status;
        private String priority;
        private LocalDate dueDate;
    }
}
