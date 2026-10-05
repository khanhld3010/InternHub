package org.example.internservice.mission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.mission.dto.request.CreateMissionItemRequest;
import org.example.internservice.mission.dto.request.UpdateItemStatusRequest;
import org.example.internservice.mission.dto.request.UpdateMissionItemRequest;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.mission.service.MissionItemService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mission Item Controller", description = "Quản lý mục công việc chi tiết và 3 trạng thái cho Mentor")
public class MissionItemController {

    private final MissionItemService missionItemService;
    private final org.example.internservice.mission.service.InternMissionService internMissionService;

    @Operation(summary = "Tạo mục công việc chi tiết trong Bảng nhiệm vụ (Chọn 1 hoặc nhiều TTS)")
    @PostMapping("/api/mission-boards/{boardId}/items")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionItemResponse>> createItem(
            @PathVariable Long boardId,
            @Valid @RequestBody CreateMissionItemRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        MissionItemResponse response = missionItemService.createItem(boardId, request, userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo mục công việc thành công", response));
    }

    @Operation(summary = "Cập nhật nội dung mục công việc chi tiết")
    @PutMapping("/api/mission-items/{itemId}")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionItemResponse>> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateMissionItemRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        MissionItemResponse response = missionItemService.updateItem(itemId, request, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Cập nhật mục công việc thành công", response));
    }

    @Operation(summary = "Cập nhật trạng thái công việc (TODO - Chưa làm, IN_PROGRESS - Đang làm, COMPLETED - Hoàn thiện)")
    @PatchMapping("/api/mission-items/{itemId}/status")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN', 'INTERN')")
    public ResponseEntity<ApiResponse<MissionItemResponse>> updateItemStatus(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateItemStatusRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        boolean isInternOnly = userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_INTERN"))
                && userDetails.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_MENTOR") || a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));

        MissionItemResponse response;
        if (isInternOnly) {
            response = internMissionService.updateKanbanStatus(itemId,
                    org.example.internservice.mission.dto.request.UpdateKanbanStatusRequest.builder()
                            .status(request.getStatus())
                            .submissionUrl(request.getSubmissionUrl())
                            .completionNote(request.getCompletionNote())
                            .build(),
                    userDetails);
        } else {
            response = missionItemService.updateItemStatus(itemId, request, userDetails);
        }
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Cập nhật trạng thái công việc thành công", response));
    }

    @Operation(summary = "Xóa mục công việc chi tiết")
    @DeleteMapping("/api/mission-items/{itemId}")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @PathVariable Long itemId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        missionItemService.deleteItem(itemId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Xóa mục công việc thành công", null));
    }

    private CustomUserDetails extractUserDetails(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        return null;
    }
}
