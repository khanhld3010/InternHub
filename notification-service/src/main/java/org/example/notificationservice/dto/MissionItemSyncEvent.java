package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionItemSyncEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String action; // CREATED, UPDATED, STATUS_CHANGED, DELETED, ARCHIVED
    private Long itemId;
    private Long boardId;
    private Long programId;
    private Long actorId;
    private Set<Long> assigneeIds;
    private String status;
    private String title;
    private String description;
    private String priority;
    private String dueDate;
}
