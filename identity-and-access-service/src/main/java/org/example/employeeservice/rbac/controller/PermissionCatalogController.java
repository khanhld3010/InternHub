package org.example.employeeservice.rbac.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.rbac.dto.response.PermissionModuleGroupResponse;
import org.example.employeeservice.rbac.service.PermissionCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
@Tag(name = "Permission Catalog Controller", description = "Truy vấn danh mục đặc quyền hệ thống theo module")
public class PermissionCatalogController {

    private final PermissionCatalogService permissionCatalogService;

    @Operation(summary = "Lấy toàn bộ danh mục đặc quyền gom theo từng Module nghiệp vụ")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_VIEW')")
    public ResponseEntity<ApiResponse<List<PermissionModuleGroupResponse>>> getAllPermissionsGrouped() {
        log.info("API: Yêu cầu lấy danh mục đặc quyền gom theo module");
        List<PermissionModuleGroupResponse> response = permissionCatalogService.getAllPermissionsGroupedByModule();
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh mục đặc quyền thành công", response));
    }
}
