package org.example.internservice.leave.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.UnauthorizedException;
import org.example.internservice.leave.dto.request.ApproveLeaveRequest;
import org.example.internservice.leave.dto.request.CreateLeaveRequest;
import org.example.internservice.leave.dto.request.RejectLeaveRequest;
import org.example.internservice.leave.dto.response.LeaveRequestResponse;
import org.example.internservice.leave.dto.response.LeaveRequestSummaryResponse;
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.example.internservice.leave.service.LeaveRequestService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leave-requests")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Leave Request Controller", description = "Quản lý đơn xin nghỉ phép của Thực tập sinh (TM-28)")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @Operation(summary = "Thực tập sinh nộp đơn xin nghỉ phép mới")
    @PostMapping
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> createLeaveRequest(
            @Valid @RequestBody CreateLeaveRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("Nhận yêu cầu nộp đơn xin nghỉ phép từ User ID: {}", userDetails.getUserId());
        LeaveRequestResponse response = leaveRequestService.createLeaveRequest(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Nộp đơn xin nghỉ phép thành công. Đơn của bạn đã được gửi tới Mentor phụ trách.", response));
    }

    @Operation(summary = "Lấy lịch sử đơn xin nghỉ phép của bản thân (Thực tập sinh)")
    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestSummaryResponse>>> getMyLeaveRequests(
            @RequestParam(value = "status", required = false) LeaveStatus status,
            @RequestParam(value = "year", required = false) Integer year,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        PageResponse<LeaveRequestSummaryResponse> response = leaveRequestService.getMyLeaveRequests(userDetails.getUserId(), status, year, pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách đơn xin nghỉ phép thành công", response));
    }

    @Operation(summary = "Thực tập sinh chủ động hủy đơn xin nghỉ phép (Chỉ khi còn PENDING)")
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> cancelLeaveRequest(
            @PathVariable("id") Long id,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("Nhận yêu cầu hủy đơn xin nghỉ phép ID: {} từ User ID: {}", id, userDetails.getUserId());
        LeaveRequestResponse response = leaveRequestService.cancelLeaveRequest(userDetails.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Hủy đơn xin nghỉ phép thành công", response));
    }

    @Operation(summary = "Xem chi tiết một đơn xin nghỉ phép cụ thể")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('INTERN', 'MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> getLeaveRequestDetail(
            @PathVariable("id") Long id,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        LeaveRequestResponse response = leaveRequestService.getLeaveRequestDetail(userDetails.getUserId(), roles, id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy thông tin chi tiết đơn xin nghỉ phép thành công", response));
    }

    @Operation(summary = "Danh sách đơn xin nghỉ phép chờ duyệt dành cho Mentor / HR")
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestSummaryResponse>>> getPendingRequests(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        PageResponse<LeaveRequestSummaryResponse> response = leaveRequestService.getPendingRequests(userDetails, pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách đơn xin nghỉ phép chờ duyệt thành công", response));
    }

    @Operation(summary = "Mentor / HR phê duyệt đơn xin nghỉ phép")
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approveLeaveRequest(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) ApproveLeaveRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("User {} đang duyệt đơn xin nghỉ phép ID: {}", userDetails.getUsername(), id);
        LeaveRequestResponse response = leaveRequestService.approveLeaveRequest(userDetails, id, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Phê duyệt đơn xin nghỉ phép thành công", response));
    }

    @Operation(summary = "Mentor / HR từ chối đơn xin nghỉ phép")
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> rejectLeaveRequest(
            @PathVariable("id") Long id,
            @Valid @RequestBody RejectLeaveRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("User {} đang từ chối đơn xin nghỉ phép ID: {}", userDetails.getUsername(), id);
        LeaveRequestResponse response = leaveRequestService.rejectLeaveRequest(userDetails, id, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Đã từ chối đơn xin nghỉ phép", response));
    }

    private CustomUserDetails extractUserDetails(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        throw new UnauthorizedException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }
}
