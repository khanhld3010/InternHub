package org.example.internservice.meeting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.exception.UnauthorizedException;
import org.example.internservice.meeting.dto.request.CreateMeetingScheduleRequest;
import org.example.internservice.meeting.dto.request.UpdateMeetingScheduleRequest;
import org.example.internservice.meeting.dto.response.MeetingScheduleResponse;
import org.example.internservice.meeting.service.MeetingScheduleService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Meeting Schedule API", description = "Quản lý lịch họp thực tập sinh, kích hoạt gửi Email & thông báo Realtime")
public class MeetingScheduleController {

    private final MeetingScheduleService meetingScheduleService;

    @PostMapping
    @Operation(summary = "Tạo lịch họp mới (Mentor hoặc HR)")
    public ResponseEntity<ApiResponse<MeetingScheduleResponse>> createMeeting(
            Authentication authentication,
            @Valid @RequestBody CreateMeetingScheduleRequest request) {

        CustomUserDetails user = extractUserDetails(authentication);
        log.info("REST: Tao lich hop moi boi userId={}, role={}", user.getUserId(), user.getRole());
        MeetingScheduleResponse response = meetingScheduleService.createMeeting(user.getUserId(), user.getUsername(), user.getRole(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Tạo lịch họp thành công và đã phát thông báo", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin lịch họp")
    public ResponseEntity<ApiResponse<MeetingScheduleResponse>> updateMeeting(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateMeetingScheduleRequest request) {

        CustomUserDetails user = extractUserDetails(authentication);
        log.info("REST: Cap nhat lich hop id={} boi userId={}", id, user.getUserId());
        MeetingScheduleResponse response = meetingScheduleService.updateMeeting(id, user.getUserId(), user.getRole(), request);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật lịch họp thành công", response));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Hủy lịch họp")
    public ResponseEntity<ApiResponse<MeetingScheduleResponse>> cancelMeeting(
            @PathVariable Long id,
            Authentication authentication) {

        CustomUserDetails user = extractUserDetails(authentication);
        log.info("REST: Huy lich hop id={} boi userId={}", id, user.getUserId());
        MeetingScheduleResponse response = meetingScheduleService.cancelMeeting(id, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.success(200, "Đã hủy lịch họp thành công", response));
    }

    @GetMapping("/my-schedules")
    @Operation(summary = "Lấy danh sách lịch họp của user đang đăng nhập")
    public ResponseEntity<ApiResponse<List<MeetingScheduleResponse>>> getMyMeetings(
            Authentication authentication) {

        CustomUserDetails user = extractUserDetails(authentication);
        log.info("REST: getMyMeetings userId={}, role={}", user.getUserId(), user.getRole());
        List<MeetingScheduleResponse> list = meetingScheduleService.getMyMeetings(user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách lịch họp thành công", list));
    }

    @GetMapping("/intern/{internUserId}")
    @Operation(summary = "Lấy danh sách lịch họp theo ID thực tập sinh")
    public ResponseEntity<ApiResponse<List<MeetingScheduleResponse>>> getMeetingsByIntern(
            @PathVariable Long internUserId) {

        List<MeetingScheduleResponse> list = meetingScheduleService.getMeetingsByIntern(internUserId);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách lịch họp thực tập sinh thành công", list));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết lịch họp")
    public ResponseEntity<ApiResponse<MeetingScheduleResponse>> getMeetingById(
            @PathVariable Long id) {

        MeetingScheduleResponse response = meetingScheduleService.getMeetingById(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy chi tiết lịch họp thành công", response));
    }

    private CustomUserDetails extractUserDetails(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        throw new UnauthorizedException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }
}

