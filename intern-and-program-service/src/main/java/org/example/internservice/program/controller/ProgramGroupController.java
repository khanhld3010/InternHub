package org.example.internservice.program.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.program.dto.request.BatchApplyGroupsRequest;
import org.example.internservice.program.dto.request.CreateGroupRequest;
import org.example.internservice.program.dto.response.InternGroupResponse;
import org.example.internservice.program.service.InternGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/programs/{programId}/groups")
@RequiredArgsConstructor
@Tag(name = "Program Intern Groups", description = "APIs quản lý nhóm thực tập và phân bổ thành viên trong chương trình")
public class ProgramGroupController {

    private final InternGroupService internGroupService;

    @Operation(summary = "Lấy danh sách nhóm thực tập của chương trình")
    @GetMapping
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")
    public ResponseEntity<ApiResponse<List<InternGroupResponse>>> getGroups(
            @PathVariable Long programId
    ) {
        List<InternGroupResponse> response = internGroupService.getGroupsByProgramId(programId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Tạo một nhóm thực tập mới (HR/Admin)")
    @PostMapping
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<InternGroupResponse>> createGroup(
            @PathVariable Long programId,
            @Valid @RequestBody CreateGroupRequest request
    ) {
        InternGroupResponse response = internGroupService.createGroup(programId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo nhóm thực tập thành công", response));
    }

    @Operation(summary = "Cập nhật thông tin nhóm thực tập (HR/Admin)")
    @PutMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<InternGroupResponse>> updateGroup(
            @PathVariable Long programId,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateGroupRequest request
    ) {
        InternGroupResponse response = internGroupService.updateGroup(programId, groupId, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật nhóm thực tập thành công", response));
    }

    @Operation(summary = "Giải tán nhóm thực tập (HR/Admin)")
    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> disbandGroup(
            @PathVariable Long programId,
            @PathVariable Long groupId
    ) {
        internGroupService.disbandGroup(programId, groupId);
        return ResponseEntity.ok(ApiResponse.success(200, "Giải tán nhóm thực tập thành công", null));
    }

    @Operation(summary = "Áp dụng chia nhóm tự động hàng loạt từ Preview (HR/Admin)")
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<InternGroupResponse>>> batchApplyGroups(
            @PathVariable Long programId,
            @Valid @RequestBody BatchApplyGroupsRequest request
    ) {
        List<InternGroupResponse> response = internGroupService.batchApplyGroups(programId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Áp dụng chia nhóm tự động thành công", response));
    }

    @Operation(summary = "Thêm một thực tập sinh vào nhóm (HR/Admin)")
    @PostMapping("/{groupId}/members/{internId}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> addMember(
            @PathVariable Long programId,
            @PathVariable Long groupId,
            @PathVariable Long internId
    ) {
        internGroupService.addMemberToGroup(programId, groupId, internId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Thêm thành viên vào nhóm thành công", null));
    }

    @Operation(summary = "Gỡ một thực tập sinh khỏi nhóm (HR/Admin)")
    @DeleteMapping("/{groupId}/members/{internId}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long programId,
            @PathVariable Long groupId,
            @PathVariable Long internId
    ) {
        internGroupService.removeMemberFromGroup(programId, groupId, internId);
        return ResponseEntity.ok(ApiResponse.success(200, "Gỡ thành viên khỏi nhóm thành công", null));
    }
}
