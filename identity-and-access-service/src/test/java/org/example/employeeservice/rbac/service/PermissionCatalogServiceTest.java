package org.example.employeeservice.rbac.service;

import org.example.employeeservice.entity.Permission;
import org.example.employeeservice.rbac.dto.response.PermissionModuleGroupResponse;
import org.example.employeeservice.rbac.dto.response.PermissionResponse;
import org.example.employeeservice.rbac.service.impl.PermissionCatalogServiceImpl;
import org.example.employeeservice.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionCatalogServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionCatalogServiceImpl permissionCatalogService;

    private Permission permUserView;
    private Permission permInternView;

    @BeforeEach
    void setUp() {
        permUserView = Permission.builder()
                .id(1)
                .code("USER_VIEW")
                .name("Xem danh sách người dùng")
                .module("USER")
                .description("Mô tả")
                .build();

        permInternView = Permission.builder()
                .id(2)
                .code("INTERN_VIEW")
                .name("Xem hồ sơ thực tập")
                .module("INTERN")
                .description("Mô tả")
                .build();
    }

    @Test
    @DisplayName("Lấy danh mục permissions gom theo Module")
    void getAllPermissionsGroupedByModule_shouldReturnGroupedList() {
        when(permissionRepository.findAllByOrderByModuleAscCodeAsc())
                .thenReturn(List.of(permUserView, permInternView));

        List<PermissionModuleGroupResponse> result = permissionCatalogService.getAllPermissionsGroupedByModule();

        assertEquals(2, result.size());
        assertEquals("USER", result.get(0).getModule());
        assertEquals("Quản Lý Người Dùng & Tài Khoản", result.get(0).getModuleName());
        assertEquals(1, result.get(0).getPermissions().size());
        assertEquals("USER_VIEW", result.get(0).getPermissions().get(0).getCode());

        assertEquals("INTERN", result.get(1).getModule());
        assertEquals("Quản Lý Hồ Sơ Thực Tập Sinh", result.get(1).getModuleName());
        assertEquals(1, result.get(1).getPermissions().size());
        assertEquals("INTERN_VIEW", result.get(1).getPermissions().get(0).getCode());
    }

    @Test
    @DisplayName("Lấy danh sách tất cả permissions phẳng")
    void getAllPermissions_shouldReturnFlatList() {
        when(permissionRepository.findAllByOrderByModuleAscCodeAsc())
                .thenReturn(List.of(permUserView, permInternView));

        List<PermissionResponse> result = permissionCatalogService.getAllPermissions();

        assertEquals(2, result.size());
        assertEquals("USER_VIEW", result.get(0).getCode());
        assertEquals("INTERN_VIEW", result.get(1).getCode());
    }
}
