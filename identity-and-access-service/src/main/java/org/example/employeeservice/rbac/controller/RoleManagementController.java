package org.example.employeeservice.rbac.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.rbac.dto.request.CreateRoleRequest;
import org.example.employeeservice.rbac.dto.request.UpdateRoleRequest;
import org.example.employeeservice.rbac.dto.response.RoleDetailResponse;
import org.example.employeeservice.rbac.dto.response.RoleResponse;
import org.example.employeeservice.rbac.service.RoleManagementService;
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
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management Controller", description = "Quản trị danh mục vai trò và ma trận phân quyền hệ thống")
public class RoleManagementController {

    private final RoleManagementService roleManagementService;

    @Operation(summary = "Lấy danh sách tất cả vai trò trong hệ thống")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_VIEW')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        log.info("API: Yêu cầu lấy danh sách vai trò hệ thống");
        List<RoleResponse> response = roleManagementService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách vai trò thành công", response));
    }

    @Operation(summary = "Lấy chi tiết vai trò kèm danh sách mã quyền đã gán")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_VIEW')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> getRoleById(@PathVariable("id") Integer id) {
        log.info("API: Yêu cầu lấy chi tiết vai trò ID={}", id);
        RoleDetailResponse response = roleManagementService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy chi tiết vai trò thành công", response));
    }

    @Operation(summary = "Tạo mới một vai trò và gán các quyền ban đầu")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        log.info("API: Yêu cầu tạo mới vai trò: {}", request.getName());
        RoleDetailResponse response = roleManagementService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Tạo mới vai trò thành công", response));
    }

    @Operation(summary = "Cập nhật thông tin vai trò và danh sách quyền")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDetailResponse>> updateRole(
            @PathVariable("id") Integer id,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        log.info("API: Yêu cầu cập nhật vai trò ID={}", id);
        RoleDetailResponse response = roleManagementService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật vai trò thành công", response));
    }

    @Operation(summary = "Xóa vai trò tùy chỉnh khỏi hệ thống")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable("id") Integer id) {
        log.info("API: Yêu cầu xóa vai trò ID={}", id);
        roleManagementService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Xóa vai trò thành công", null));
    }
}
