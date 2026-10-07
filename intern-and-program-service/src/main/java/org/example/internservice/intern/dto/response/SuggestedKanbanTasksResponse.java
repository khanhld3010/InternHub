package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedKanbanTasksResponse {

    private Integer weekNumber;

    @Builder.Default
    private List<SuggestedTaskItem> completedTasks = new ArrayList<>();

    @Builder.Default
    private List<SuggestedTaskItem> unfinishedTasks = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedTaskItem {
        private Long missionItemId;
        private String title;
        private String status;
        private String statusDisplayName;
        private LocalDate dueDate;
        private Boolean isOverdue;
        private String submissionUrl;
        private String completionNote;
        private LocalDateTime submittedAt;
    }
}
