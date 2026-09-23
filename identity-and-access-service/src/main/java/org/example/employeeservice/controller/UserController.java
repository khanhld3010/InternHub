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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> toggleUserStatus(@PathVariable Integer id) {
        log.info("API: Thay đổi trạng thái tài khoản người dùng ID: {}", id);
        UserResponse user = userService.toggleUserStatus(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật trạng thái tài khoản thành công", user));
    }
}
