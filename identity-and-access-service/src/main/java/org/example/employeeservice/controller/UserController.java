package org.example.employeeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.dto.response.UserResponse;
import org.example.employeeservice.service.UserService;
import org.example.employeeservice.system.audit.annotation.Auditable;
import org.example.employeeservice.system.audit.entity.AuditAction;
import org.example.employeeservice.system.audit.entity.AuditModule;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/api/employees/users", "/api/users"})
@RequiredArgsConstructor
@Tag(name = "User Controller", description = "Quản lý thông tin người dùng và trạng thái tài khoản")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Lấy danh sách toàn bộ người dùng")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        log.info("API: Lấy danh sách toàn bộ người dùng");
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách người dùng thành công", users));
    }

    @Operation(summary = "Lấy thông tin cá nhân của người dùng hiện tại")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUserProfile(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.example.employeeservice.exception.UnauthorizedException("Yêu cầu đăng nhập");
        }
        String username = authentication.getName();
        log.info("API: Lấy thông tin cá nhân cho username: {}", username);
        UserResponse user = userService.getUserByUsername(username);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy thông tin tài khoản thành công", user));
    }

    @Operation(summary = "Cập nhật thông tin cá nhân của người dùng hiện tại (Self-Service)")
    @Auditable(action = AuditAction.UPDATE_USER, module = AuditModule.USER, description = "Người dùng cập nhật thông tin cá nhân")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUserProfile(
            org.springframework.security.core.Authentication authentication,
            @jakarta.validation.Valid @RequestBody org.example.employeeservice.dto.request.UpdateUserProfileRequest request
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.example.employeeservice.exception.UnauthorizedException("Yêu cầu đăng nhập");
        }
        String username = authentication.getName();
        log.info("API: Người dùng {} cập nhật hồ sơ cá nhân", username);
        UserResponse response = userService.updateCurrentUserProfile(username, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật hồ sơ thành công", response));
    }

    @Operation(summary = "Lấy thông tin chi tiết người dùng theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Integer id) {
        log.info("API: Lấy thông tin người dùng có ID: {}", id);
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy thông tin người dùng thành công", user));
    }

    @Operation(summary = "Khóa hoặc mở khóa tài khoản người dùng")
    @Auditable(action = AuditAction.TOGGLE_USER_STATUS, module = AuditModule.USER, description = "Thay đổi trạng thái tài khoản người dùng")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> toggleUserStatus(@PathVariable Integer id) {
        log.info("API: Thay đổi trạng thái tài khoản người dùng ID: {}", id);
        UserResponse user = userService.toggleUserStatus(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật trạng thái tài khoản thành công", user));
    }

    @Operation(summary = "Lấy thông tin tài khoản cá nhân của chính mình (Self-service)")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(org.springframework.security.core.Authentication authentication) {
        String username = authentication.getName();
        log.info("API: Lấy thông tin cá nhân của user: {}", username);
        UserResponse response = userService.getCurrentUserProfile(username);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy thông tin tài khoản thành công", response));
    }

    @Operation(summary = "Cập nhật thông tin hồ sơ cá nhân (Self-service, áp dụng chung cả 4 role)")
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> updateOwnProfile(
            @jakarta.validation.Valid @RequestBody org.example.employeeservice.dto.request.UpdateProfileRequest request,
            org.springframework.security.core.Authentication authentication) {
        String username = authentication.getName();
        log.info("API: Cập nhật thông tin cá nhân cho user: {}", username);
        UserResponse response = userService.updateOwnProfile(username, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật thông tin cá nhân thành công", response));
    }
}
