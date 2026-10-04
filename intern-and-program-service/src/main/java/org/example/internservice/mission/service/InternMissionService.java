package org.example.internservice.mission.service;

import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.mission.dto.request.UpdateKanbanStatusRequest;
import org.example.internservice.mission.dto.response.InternKanbanBoardResponse;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;

public interface InternMissionService {

    PageResponse<MissionItemResponse> getMyMissions(
            CustomUserDetails userDetails,
            MissionItemStatus status,
            Long boardId,
            MissionPriority priority,
            String keyword,
            Pageable pageable
    );

    InternKanbanBoardResponse getMyKanbanBoard(CustomUserDetails userDetails);

    MissionItemResponse getMissionDetail(Long itemId, CustomUserDetails userDetails);

    MissionItemResponse updateKanbanStatus(Long itemId, UpdateKanbanStatusRequest request, CustomUserDetails userDetails);
}
