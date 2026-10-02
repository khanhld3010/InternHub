package org.example.internservice.mission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.mission.entity.enums.BoardStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionBoardDetailResponse {

    private Long id;
    private Long programId;
    private String programName;
    private Long mentorId;
    private String mentorName;
    private String title;
    private String description;
    private BoardStatus status;
    private String statusDisplayName;
    private Integer totalItems;
    private Integer todoCount;
    private Integer inProgressCount;
    private Integer completedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<MissionItemResponse> todoItems = new ArrayList<>();

    @Builder.Default
    private List<MissionItemResponse> inProgressItems = new ArrayList<>();

    @Builder.Default
    private List<MissionItemResponse> completedItems = new ArrayList<>();
}
