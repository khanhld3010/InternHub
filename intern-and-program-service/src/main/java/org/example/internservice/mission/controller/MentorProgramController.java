package org.example.internservice.mission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.mission.dto.response.AssigneeResponse;
import org.example.internservice.mission.dto.response.MentorProgramResponse;
import org.example.internservice.mission.service.MissionBoardService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mentor")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mentor Program Controller", description = "Các API tra cứu chương trình và thực tập sinh dành cho Mentor")
public class MentorProgramController {

    private final MissionBoardService missionBoardService;

    @Operation(summary = "Lấy danh sách các Chương trình thực tập mà Mentor đăng nhập đang phụ trách")
    @GetMapping("/programs")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<MentorProgramResponse>>> getMyMentoredPrograms(
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        List<MentorProgramResponse> response = missionBoardService.getMyMentoredPrograms(userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách chương trình phụ trách thành công", response));
    }

    @Operation(summary = "Lấy danh sách Thực tập sinh trong Chương trình để Mentor chọn khi giao việc")
    @GetMapping("/programs/{programId}/interns")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AssigneeResponse>>> getProgramInterns(
            @PathVariable Long programId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        List<AssigneeResponse> response = missionBoardService.getProgramInterns(programId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách thực tập sinh trong chương trình thành công", response));
    }

    private CustomUserDetails extractUserDetails(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        return null;
    }
}
