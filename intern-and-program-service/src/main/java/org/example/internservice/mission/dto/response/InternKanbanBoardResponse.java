package org.example.internservice.mission.dto.response;

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
public class InternKanbanBoardResponse {

    private List<MissionItemResponse> todoItems;
    private List<MissionItemResponse> inProgressItems;
    private List<MissionItemResponse> completedItems;
    private int totalCount;
    private int todoCount;
    private int inProgressCount;
    private int completedCount;
}
