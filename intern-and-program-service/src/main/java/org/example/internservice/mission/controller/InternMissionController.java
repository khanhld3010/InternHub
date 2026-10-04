package org.example.internservice.mission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.mission.dto.request.UpdateKanbanStatusRequest;
import org.example.internservice.mission.dto.response.InternKanbanBoardResponse;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.mission.service.InternMissionService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Intern Mission Controller", description = "Quản lý và chuyển đổi trạng thái nhiệm vụ Kanban cho Thực tập sinh (TM-20)")
public class InternMissionController {

    private final InternMissionService internMissionService;

    @Operation(summary = "Lấy danh sách nhiệm vụ được phân công cho Thực tập sinh (hỗ trợ lọc & phân trang)")
    @GetMapping({"/api/mission-items/my-missions", "/api/intern/mission-items"})
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<PageResponse<MissionItemResponse>>> getMyMissions(
            @RequestParam(required = false) MissionItemStatus status,
            @RequestParam(required = false) Long boardId,
            @RequestParam(required = false) MissionPriority priority,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        PageResponse<MissionItemResponse> response = internMissionService.getMyMissions(
                userDetails, status, boardId, priority, keyword, pageable
        );
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách nhiệm vụ thành công", response));
    }

    @Operation(summary = "Lấy danh sách nhiệm vụ cá nhân phân nhóm 3 cột Kanban (TODO, IN_PROGRESS, COMPLETED)")
    @GetMapping({"/api/mission-items/my-missions/kanban", "/api/intern/mission-items/kanban"})
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<InternKanbanBoardResponse>> getMyKanbanBoard(
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        InternKanbanBoardResponse response = internMissionService.getMyKanbanBoard(userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy bảng Kanban nhiệm vụ cá nhân thành công", response));
    }

    @Operation(summary = "Xem chi tiết mục công việc được phân công")
    @GetMapping({"/api/mission-items/{itemId}", "/api/intern/mission-items/{itemId}"})
    @PreAuthorize("hasAnyRole('INTERN', 'MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionItemResponse>> getMissionDetail(
            @PathVariable Long itemId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        MissionItemResponse response = internMissionService.getMissionDetail(itemId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy chi tiết mục công việc thành công", response));
    }

    @Operation(summary = "Thực tập sinh chuyển đổi trạng thái nhiệm vụ Kanban (TODO, IN_PROGRESS, COMPLETED)")
    @PatchMapping({"/api/intern/mission-items/{itemId}/status", "/api/mission-items/my-missions/{itemId}/status"})
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<MissionItemResponse>> updateInternKanbanStatus(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateKanbanStatusRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        MissionItemResponse response = internMissionService.updateKanbanStatus(itemId, request, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Cập nhật trạng thái công việc thành công", response));
    }

    private CustomUserDetails extractUserDetails(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        return null;
    }
}
