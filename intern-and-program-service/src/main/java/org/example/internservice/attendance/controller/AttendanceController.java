package org.example.internservice.attendance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.attendance.dto.request.AttendanceAppealRequest;
import org.example.internservice.attendance.dto.request.CheckInConfirmRequest;
import org.example.internservice.attendance.dto.request.CheckInInitiateRequest;
import org.example.internservice.attendance.dto.request.CheckOutRequest;
import org.example.internservice.attendance.dto.request.ConfirmForgottenCheckoutRequest;
import org.example.internservice.attendance.dto.request.QuickCheckOutRequest;
import org.example.internservice.attendance.dto.request.ReviewAttendanceRequest;
import org.example.internservice.attendance.dto.response.AttendanceResponse;
import org.example.internservice.attendance.dto.response.CheckInQrResponse;
import org.example.internservice.attendance.dto.response.MonthlyAttendanceSummaryResponse;
import org.example.internservice.attendance.dto.response.TodayAttendanceResponse;
import org.example.internservice.attendance.service.AttendanceService;
import org.example.internservice.attendance.service.SseEmitterRegistry;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.exception.UnauthorizedException;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/attendances")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Attendance Controller", description = "Quản lý chấm công Check-in / Check-out cho Thực tập sinh (TM-25)")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final SseEmitterRegistry sseEmitterRegistry;

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

    @Operation(summary = "Lắng nghe sự kiện Check-in real-time qua SSE (Phone -> Laptop Sync)")
    @GetMapping(value = "/check-in/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCheckInStatus(
            @RequestParam("qrToken") String qrToken,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        // Thiết lập header chuẩn chống proxy / Nginx / Gateway đệm dữ liệu SSE
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
        log.info("Khởi tạo kết nối SSE stream lắng nghe cho qrToken: {}", qrToken);
        return sseEmitterRegistry.register(qrToken);
    }

    @Operation(summary = "Xác nhận Check-in bằng mã QR (Lớp xác thực 2 bước & Trust Engine)")
    @PostMapping("/check-in/confirm")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> confirmCheckIn(
            @Valid @RequestBody CheckInConfirmRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        String clientIp = extractClientIp(servletRequest);
        log.info("Nhận request xác nhận check-in từ userId: {}, qrToken: {}, clientIp: {}", userId, request.getQrToken(), clientIp);
        AttendanceResponse response = attendanceService.confirmCheckIn(userId, request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Check-in thành công", response));
    }

    @Operation(summary = "Tan ca nhanh 1-chạm trên Web Mobile (Adaptive Check-out)")
    @PostMapping("/check-out/quick")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> quickCheckOut(
            @Valid @RequestBody QuickCheckOutRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        String clientIp = extractClientIp(servletRequest);
        log.info("Nhận request tan ca nhanh từ userId: {}, clientIp: {}", userId, clientIp);
        AttendanceResponse response = attendanceService.quickCheckOut(userId, request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Tan ca thành công", response));
    }

    @Operation(summary = "Phê duyệt hoặc từ chối ca chấm công ngoại lệ (HR / ADMIN)")
    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> reviewAttendance(
            @PathVariable("id") Long attendanceId,
            @Valid @RequestBody ReviewAttendanceRequest request,
            Authentication authentication
    ) {
        String reviewerUsername = authentication != null ? authentication.getName() : "HR_ADMIN";
        log.info("HR [{}] thực hiện xử lý duyệt ca chấm công ID {}: Status={}", reviewerUsername, attendanceId, request.getReviewStatus());
        AttendanceResponse response = attendanceService.reviewAttendance(attendanceId, request, reviewerUsername);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Xử lý duyệt ca chấm công thành công", response));
    }

    @Operation(summary = "Thực tập sinh gửi giải trình / khiếu nại chấm công (INTERN)")
    @PostMapping("/{id}/appeal")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> appealAttendance(
            @PathVariable("id") Long attendanceId,
            @Valid @RequestBody AttendanceAppealRequest request,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        log.info("Intern userId [{}] gửi giải trình chấm công ID {}: {}", userId, attendanceId, request.getReason());
        AttendanceResponse response = attendanceService.appealAttendance(userId, attendanceId, request);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Gửi giải trình chấm công thành công", response));
    }

    @Operation(summary = "Mentor hoặc HR xác nhận giờ ra thực tế cho ca quên check-out (MENTOR / HR / ADMIN)")
    @PostMapping("/{id}/confirm-checkout")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> confirmForgottenCheckout(
            @PathVariable("id") Long attendanceId,
            @Valid @RequestBody ConfirmForgottenCheckoutRequest request,
            Authentication authentication
    ) {
        String confirmedBy = authentication != null ? authentication.getName() : "MENTOR_HR";
        log.info("Người dùng [{}] xác nhận giờ check-out thực tế cho ca chấm công ID {}: {}", confirmedBy, attendanceId, request.getActualCheckOutTime());
        AttendanceResponse response = attendanceService.confirmForgottenCheckout(attendanceId, request, confirmedBy);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Xác nhận giờ check-out thành công", response));
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

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
