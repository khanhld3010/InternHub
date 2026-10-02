package org.example.internservice.mission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.mission.dto.request.CreateMissionBoardRequest;
import org.example.internservice.mission.dto.request.UpdateMissionBoardRequest;
import org.example.internservice.mission.dto.response.MissionBoardDetailResponse;
import org.example.internservice.mission.dto.response.MissionBoardResponse;
import org.example.internservice.mission.service.MissionBoardService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mission Board Controller", description = "Quản lý Bảng nhiệm vụ (MissionBoard) cho Mentor")
public class MissionBoardController {

    private final MissionBoardService missionBoardService;

    @Operation(summary = "Tạo Bảng nhiệm vụ mới cho Chương trình thực tập")
    @PostMapping("/api/programs/{programId}/mission-boards")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionBoardResponse>> createBoard(
            @PathVariable Long programId,
            @Valid @RequestBody CreateMissionBoardRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        MissionBoardResponse response = missionBoardService.createBoard(programId, request, userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo bảng nhiệm vụ thành công", response));
    }

    @Operation(summary = "Lấy danh sách Bảng nhiệm vụ của Chương trình")
    @GetMapping("/api/programs/{programId}/mission-boards")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<MissionBoardResponse>>> getBoardsByProgram(
            @PathVariable Long programId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        List<MissionBoardResponse> response = missionBoardService.getBoardsByProgram(programId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách bảng nhiệm vụ thành công", response));
    }

    @Operation(summary = "Xem chi tiết Bảng nhiệm vụ (kèm các mục công việc phân theo 3 cột)")
    @GetMapping("/api/mission-boards/{boardId}")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionBoardDetailResponse>> getBoardDetail(
            @PathVariable Long boardId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        MissionBoardDetailResponse response = missionBoardService.getBoardDetail(boardId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy chi tiết bảng nhiệm vụ thành công", response));
    }

    @Operation(summary = "Cập nhật thông tin Bảng nhiệm vụ")
    @PutMapping("/api/mission-boards/{boardId}")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionBoardResponse>> updateBoard(
            @PathVariable Long boardId,
            @Valid @RequestBody UpdateMissionBoardRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        MissionBoardResponse response = missionBoardService.updateBoard(boardId, request, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Cập nhật bảng nhiệm vụ thành công", response));
    }

    @Operation(summary = "Xóa Bảng nhiệm vụ")
    @DeleteMapping("/api/mission-boards/{boardId}")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBoard(
            @PathVariable Long boardId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        missionBoardService.deleteBoard(boardId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Xóa bảng nhiệm vụ thành công", null));
    }

    private CustomUserDetails extractUserDetails(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        return null;
    }
}
