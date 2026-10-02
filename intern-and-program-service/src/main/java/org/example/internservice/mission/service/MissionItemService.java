package org.example.internservice.mission.service;

import org.example.internservice.mission.dto.request.CreateMissionItemRequest;
import org.example.internservice.mission.dto.request.UpdateItemStatusRequest;
import org.example.internservice.mission.dto.request.UpdateMissionItemRequest;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.security.CustomUserDetails;

public interface MissionItemService {

    MissionItemResponse createItem(Long boardId, CreateMissionItemRequest request, CustomUserDetails userDetails);

    MissionItemResponse updateItem(Long itemId, UpdateMissionItemRequest request, CustomUserDetails userDetails);

    MissionItemResponse updateItemStatus(Long itemId, UpdateItemStatusRequest request, CustomUserDetails userDetails);

    void deleteItem(Long itemId, CustomUserDetails userDetails);
}
