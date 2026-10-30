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
    @PreAuthorize("hasRole('ADMIN') or 'true'.equalsIgnoreCase(#internalCall)")
    public ResponseEntity<ApiResponse<UserResponse>> toggleUserStatus(
            @PathVariable Integer id,
            @RequestHeader(value = "X-Internal-Call", required = false) String internalCall) {
        log.info("API: Thay đổi trạng thái tài khoản người dùng ID: {}, internalCall={}", id, internalCall);
        UserResponse user = userService.toggleUserStatus(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật trạng thái tài khoản thành công", user));
    }

    @Operation(summary = "Khóa cưỡng chế tài khoản người dùng (Offboarding / Thu hồi quyền an toàn)")
    @Auditable(action = AuditAction.TOGGLE_USER_STATUS, module = AuditModule.USER, description = "Khóa cưỡng chế tài khoản người dùng")
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN') or 'true'.equalsIgnoreCase(#internalCall)")
    public ResponseEntity<ApiResponse<UserResponse>> forceLockUserAccount(
            @PathVariable Integer id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @RequestHeader(value = "X-Internal-Call", required = false) String internalCall) {
        String reason = body != null ? body.get("reason") : "Khóa tài khoản bởi hệ thống (Offboarding)";
        log.warn("API: Khóa cưỡng chế tài khoản người dùng ID: {}, reason={}, internalCall={}", id, reason, internalCall);
        UserResponse user = userService.lockUserAccount(id, reason);
        return ResponseEntity.ok(ApiResponse.success(200, "Đã khóa cưỡng chế tài khoản thành công", user));
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
    @Auditable(action = AuditAction.UPDATE_USER, module = AuditModule.USER, description = "Người dùng cập nhật thông tin cá nhân")
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

    @Operation(summary = "Admin đặt lại mật khẩu cho người dùng")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Integer id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String newPassword = body != null ? body.get("newPassword") : "123456";
        log.info("API: Admin đặt lại mật khẩu cho userId={}", id);
        userService.resetUserPassword(id, newPassword);
        return ResponseEntity.ok(ApiResponse.success(200, "Đặt lại mật khẩu thành công", null));
    }

    @Operation(summary = "Admin cập nhật vai trò cho người dùng")
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable Integer id,
            @RequestBody java.util.Map<String, String> body) {
        String roleName = body != null ? body.get("role") : null;
        if (roleName == null || roleName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Tên vai trò không được để trống"));
        }
        log.info("API: Admin cập nhật vai trò cho userId={} thành {}", id, roleName);
        UserResponse updated = userService.updateUserRole(id, roleName.trim());
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật vai trò người dùng thành công", updated));
    }
}
