package org.example.employeeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.dto.request.ActivateAccountRequest;
import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.request.RegisterRequest;
import org.example.employeeservice.dto.request.ResendActivationRequest;
import org.example.employeeservice.dto.response.ApiResponse;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.dto.response.RegisterResponse;
import org.example.employeeservice.service.AuthService;
import org.example.employeeservice.system.audit.annotation.Auditable;
import org.example.employeeservice.system.audit.entity.AuditAction;
import org.example.employeeservice.system.audit.entity.AuditModule;
import org.example.employeeservice.rbac.dto.response.UserPermissionsResponse;
import org.example.employeeservice.rbac.service.RoleManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/employees/auth", "/api/auth"})
@RequiredArgsConstructor
@Tag(name = "Authentication Controller", description = "Quản lý đăng nhập, đăng ký tài khoản, kích hoạt email và thông tin phiên người dùng")
public class AuthController {

    private final AuthService authService;
    private final RoleManagementService roleManagementService;

    @Operation(summary = "Đăng nhập hệ thống bằng username và password")
    @Auditable(action = AuditAction.LOGIN_SUCCESS, module = AuditModule.AUTH, description = "Người dùng đăng nhập vào hệ thống")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Nhận yêu cầu đăng nhập từ user: {}", request.getUsername());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @Operation(summary = "Đăng ký tài khoản người dùng mới (Public Endpoint - Nhận 1 Object tổng hợp)")
    @Auditable(action = AuditAction.REGISTER, module = AuditModule.AUTH, description = "Đăng ký tài khoản người dùng mới")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Nhận yêu cầu đăng ký tài khoản từ username: {}, email: {}", request.getUsername(), request.getEmail());
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công! Vui lòng kiểm tra email để nhận mã kích hoạt.", response));
    }

    @Operation(summary = "Xác thực mã OTP kích hoạt tài khoản (TM-10 v1.4)")
    @Auditable(action = AuditAction.ACTIVATE_SUCCESS, module = AuditModule.AUTH, description = "Kích hoạt tài khoản người dùng qua mã OTP")
    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<Void>> activateAccount(@Valid @RequestBody ActivateAccountRequest request) {
        log.info("Nhận yêu cầu kích hoạt tài khoản cho: {}", request.getIdentifier());
        authService.activateAccount(request);
        return ResponseEntity.ok(ApiResponse.success("Kích hoạt tài khoản thành công! Bạn có thể đăng nhập ngay bây giờ.", null));
    }

    @Operation(summary = "Gửi lại mã OTP kích hoạt tài khoản (TM-10 v1.4)")
    @Auditable(action = AuditAction.RESEND_ACTIVATION, module = AuditModule.AUTH, description = "Yêu cầu gửi lại mã kích hoạt tài khoản")
    @PostMapping("/resend-activation")
    public ResponseEntity<ApiResponse<Void>> resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        log.info("Nhận yêu cầu gửi lại mã kích hoạt cho: {}", request.getIdentifier());
        authService.resendActivation(request);
        return ResponseEntity.ok(ApiResponse.success("Mã kích hoạt mới đã được gửi vào hòm thư email của bạn.", null));
    }

    @Operation(summary = "Lấy thông tin tài khoản đang đăng nhập")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Chưa xác thực"));
        }
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", authentication.getName());
        userInfo.put("authorities", authentication.getAuthorities());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tài khoản thành công", userInfo));
    }

    @Operation(summary = "Đổi mật khẩu người dùng đang đăng nhập")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody org.example.employeeservice.dto.request.ChangePasswordRequest request
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Chưa xác thực"));
        }
        String username = authentication.getName();
        log.info("API: Đổi mật khẩu cho người dùng: {}", username);
        authService.changePassword(username, request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công!", null));
    }

    @Operation(summary = "Lấy chi tiết danh sách đặc quyền (permissions) của tài khoản đang đăng nhập")
    @GetMapping("/me/permissions")
    public ResponseEntity<ApiResponse<UserPermissionsResponse>> getCurrentUserPermissions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa xác thực"));
        }
        UserPermissionsResponse response = roleManagementService.getUserPermissions(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin quyền hạn người dùng thành công", response));
    }
}
