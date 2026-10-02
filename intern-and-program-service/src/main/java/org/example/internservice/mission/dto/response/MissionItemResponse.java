package org.example.internservice.mission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionItemResponse {

    private Long id;
    private Long boardId;
    private String title;
    private String description;
    private MissionPriority priority;
    private String priorityDisplayName;
    private MissionItemStatus status;
    private String statusDisplayName;
    private LocalDate dueDate;
    private Boolean isOverdue;
    private Integer orderIndex;
    private List<AssigneeResponse> assignees;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
