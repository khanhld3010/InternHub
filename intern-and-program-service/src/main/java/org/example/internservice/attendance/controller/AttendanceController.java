package org.example.internservice.attendance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.dto.request.CheckInConfirmRequest;
import org.example.internservice.attendance.dto.request.CheckInInitiateRequest;
import org.example.internservice.attendance.dto.request.CheckInRequest;
import org.example.internservice.attendance.dto.request.CheckOutRequest;
import org.example.internservice.attendance.dto.response.AttendanceResponse;
import org.example.internservice.attendance.dto.response.CheckInQrResponse;
import org.example.internservice.attendance.dto.response.MonthlyAttendanceSummaryResponse;
import org.example.internservice.attendance.dto.response.TodayAttendanceResponse;
import org.example.internservice.attendance.service.AttendanceService;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.exception.UnauthorizedException;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendances")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Attendance Controller", description = "Quản lý chấm công Check-in / Check-out cho Thực tập sinh (TM-25)")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "Lấy trạng thái chấm công của ngày hiện tại (Widget Dashboard)")
    @GetMapping("/today")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<TodayAttendanceResponse>> getTodayAttendance(Authentication authentication) {
        Long userId = extractUserId(authentication);
        TodayAttendanceResponse response = attendanceService.getTodayAttendance(userId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy trạng thái chấm công hôm nay thành công", response));
    }

    @Operation(summary = "Khởi tạo Check-in & Tạo mã QR xác thực phiên (Hiệu lực 60s)")
    @PostMapping("/check-in/initiate")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<CheckInQrResponse>> initiateCheckIn(
            @Valid @RequestBody CheckInInitiateRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        log.info("Nhận request khởi tạo check-in từ userId: {}, tọa độ: [{}, {}]", userId, request.getLatitude(), request.getLongitude());
        CheckInQrResponse response = attendanceService.initiateCheckIn(userId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Khoảng cách hợp lệ. Mã QR xác thực đã được tạo thành công.", response));
    }

    @Operation(summary = "Xác nhận Check-in bằng mã QR (Lớp xác thực 2 bước)")
    @PostMapping("/check-in/confirm")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> confirmCheckIn(
            @Valid @RequestBody CheckInConfirmRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        log.info("Nhận request xác nhận check-in từ userId: {}, qrToken: {}", userId, request.getQrToken());
        AttendanceResponse response = attendanceService.confirmCheckIn(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Check-in thành công", response));
    }

    @Operation(summary = "Check-in chấm công đầu ngày (Yêu cầu tọa độ GPS)")
    @PostMapping("/check-in")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        log.info("Nhận request check-in từ userId: {}, tọa độ: [{}, {}]", userId, request.getLatitude(), request.getLongitude());
        AttendanceResponse response = attendanceService.checkIn(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Check-in thành công", response));
    }

    @Operation(summary = "Check-out chấm công cuối ngày (Yêu cầu tọa độ GPS)")
    @PostMapping("/check-out")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkOut(
            @Valid @RequestBody CheckOutRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        log.info("Nhận request check-out từ userId: {}, tọa độ: [{}, {}]", userId, request.getLatitude(), request.getLongitude());
        AttendanceResponse response = attendanceService.checkOut(userId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Check-out thành công", response));
    }

    @Operation(summary = "Lấy lịch sử chấm công cá nhân theo tháng/năm")
    @GetMapping("/my-history")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<MonthlyAttendanceSummaryResponse>> getMyHistory(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        MonthlyAttendanceSummaryResponse response = attendanceService.getMyHistory(userId, month, year);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy lịch sử chấm công thành công", response));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new UnauthorizedException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }
}
