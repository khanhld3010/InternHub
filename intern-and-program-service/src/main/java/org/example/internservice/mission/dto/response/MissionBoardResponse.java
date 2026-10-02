package org.example.internservice.mission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.mission.entity.enums.BoardStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionBoardResponse {

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
}
