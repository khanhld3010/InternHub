package org.example.employeeservice.rbac.service;

import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Permission;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.exception.BadRequestException;
import org.example.employeeservice.exception.DuplicateResourceException;
import org.example.employeeservice.exception.ResourceNotFoundException;
import org.example.employeeservice.rbac.dto.request.CreateRoleRequest;
import org.example.employeeservice.rbac.dto.request.UpdateRoleRequest;
import org.example.employeeservice.rbac.dto.response.RoleDetailResponse;
import org.example.employeeservice.rbac.dto.response.RoleResponse;
import org.example.employeeservice.rbac.dto.response.UserPermissionsResponse;
import org.example.employeeservice.rbac.service.impl.RoleManagementServiceImpl;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.PermissionRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private RoleManagementServiceImpl roleManagementService;

    private Permission permUserView;
    private Permission permInternApprove;
    private Role hrRole;
    private Role customRole;

    @BeforeEach
    void setUp() {
        permUserView = Permission.builder()
                .id(1)
                .code("USER_VIEW")
                .name("Xem danh sách người dùng")
                .module("USER")
                .build();

        permInternApprove = Permission.builder()
                .id(2)
                .code("INTERN_APPROVE")
                .name("Phê duyệt hồ sơ")
                .module("INTERN")
                .build();

        Set<Permission> hrPerms = new HashSet<>();
        hrPerms.add(permUserView);
        hrPerms.add(permInternApprove);

        hrRole = Role.builder()
                .id(2)
                .name("HR")
                .description("Chuyên viên nhân sự")
                .isSystem(true)
                .permissions(hrPerms)
                .createdAt(LocalDateTime.now())
                .build();

        customRole = Role.builder()
                .id(10)
                .name("COORDINATOR")
                .description("Điều phối viên")
                .isSystem(false)
                .permissions(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Lấy danh sách vai trò thành công kèm số lượng user và permission")
    void getAllRoles_shouldReturnListWithCounts() {
        when(roleRepository.findAllWithPermissions()).thenReturn(List.of(hrRole, customRole));
        when(accountRepository.countByRoleId(2)).thenReturn(5L);
        when(accountRepository.countByRoleId(10)).thenReturn(0L);

        List<RoleResponse> result = roleManagementService.getAllRoles();

        assertEquals(2, result.size());
        assertEquals("HR", result.get(0).getName());
        assertEquals(5L, result.get(0).getUserCount());
        assertEquals(2, result.get(0).getPermissionCount());
        assertTrue(result.get(0).getIsSystem());

        assertEquals("COORDINATOR", result.get(1).getName());
        assertEquals(0L, result.get(1).getUserCount());
        assertFalse(result.get(1).getIsSystem());
    }

    @Test
    @DisplayName("Lấy chi tiết vai trò tồn tại")
    void getRoleById_whenExists_shouldReturnDetailResponse() {
        when(roleRepository.findByIdWithPermissions(2)).thenReturn(Optional.of(hrRole));
        when(accountRepository.countByRoleId(2)).thenReturn(5L);

        RoleDetailResponse response = roleManagementService.getRoleById(2);

        assertNotNull(response);
        assertEquals(2, response.getId());
        assertEquals("HR", response.getName());
        assertEquals(5L, response.getUserCount());
        assertTrue(response.getPermissions().contains("USER_VIEW"));
        assertTrue(response.getPermissions().contains("INTERN_APPROVE"));
    }

    @Test
    @DisplayName("Lấy chi tiết vai trò không tồn tại ném ResourceNotFoundException")
    void getRoleById_whenNotExists_shouldThrowResourceNotFoundException() {
        when(roleRepository.findByIdWithPermissions(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> roleManagementService.getRoleById(99));
    }

    @Test
    @DisplayName("Tạo vai trò mới với dữ liệu hợp lệ")
    void createRole_withValidData_shouldSaveSuccessfully() {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("COORDINATOR")
                .description("Điều phối viên thực tập")
                .permissionCodes(List.of("USER_VIEW"))
                .build();

        when(roleRepository.existsByNameIgnoreCase("COORDINATOR")).thenReturn(false);
        when(permissionRepository.findAllByCodeIn(any())).thenReturn(Set.of(permUserView));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role r = invocation.getArgument(0);
            r.setId(10);
            return r;
        });

        RoleDetailResponse response = roleManagementService.createRole(request);

        assertNotNull(response);
        assertEquals("COORDINATOR", response.getName());
        assertFalse(response.getIsSystem());
        assertTrue(response.getPermissions().contains("USER_VIEW"));
        verify(roleRepository, times(1)).save(any(Role.class));
    }

    @Test
    @DisplayName("Tạo vai trò với tên đã tồn tại ném DuplicateResourceException")
    void createRole_withDuplicateName_shouldThrowDuplicateException() {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("HR")
                .build();

        when(roleRepository.existsByNameIgnoreCase("HR")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> roleManagementService.createRole(request));
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo vai trò chứa permission code không tồn tại ném BadRequestException")
    void createRole_withInvalidPermissionCodes_shouldThrowBadRequestException() {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("CUSTOM_ROLE")
                .permissionCodes(List.of("INVALID_CODE"))
                .build();

        when(roleRepository.existsByNameIgnoreCase("CUSTOM_ROLE")).thenReturn(false);
        when(permissionRepository.findAllByCodeIn(any())).thenReturn(Set.of());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> roleManagementService.createRole(request));
        assertTrue(ex.getMessage().contains("INVALID_CODE"));
    }

    @Test
    @DisplayName("Cập nhật tên vai trò hệ thống ném BadRequestException")
    void updateRole_whenRoleIsSystemAndNameChanged_shouldThrowBadRequestException() {
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("NEW_HR")
                .build();

        when(roleRepository.findByIdWithPermissions(2)).thenReturn(Optional.of(hrRole));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> roleManagementService.updateRole(2, request));
        assertTrue(ex.getMessage().contains("Không thể thay đổi tên của vai trò mặc định hệ thống"));
    }

    @Test
    @DisplayName("Cập nhật vai trò tùy chỉnh thành công")
    void updateRole_withValidData_shouldUpdateSuccessfully() {
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("SUPERVISOR")
                .description("Mô tả mới")
                .permissionCodes(List.of("INTERN_APPROVE"))
                .build();

        when(roleRepository.findByIdWithPermissions(10)).thenReturn(Optional.of(customRole));
        when(roleRepository.existsByNameIgnoreCase("SUPERVISOR")).thenReturn(false);
        when(permissionRepository.findAllByCodeIn(any())).thenReturn(Set.of(permInternApprove));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleDetailResponse response = roleManagementService.updateRole(10, request);

        assertEquals("SUPERVISOR", response.getName());
        assertEquals("Mô tả mới", response.getDescription());
        assertTrue(response.getPermissions().contains("INTERN_APPROVE"));
    }

    @Test
    @DisplayName("Xóa vai trò hệ thống (isSystem=true) ném BadRequestException")
    void deleteRole_whenRoleIsSystem_shouldThrowBadRequestException() {
        when(roleRepository.findById(2)).thenReturn(Optional.of(hrRole));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> roleManagementService.deleteRole(2));
        assertTrue(ex.getMessage().contains("Không thể xóa vai trò mặc định của hệ thống"));
        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa vai trò đang có tài khoản sử dụng ném BadRequestException")
    void deleteRole_whenRoleHasAssignedUsers_shouldThrowBadRequestException() {
        when(roleRepository.findById(10)).thenReturn(Optional.of(customRole));
        when(accountRepository.countByRoleId(10)).thenReturn(3L);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> roleManagementService.deleteRole(10));
        assertTrue(ex.getMessage().contains("người dùng sử dụng"));
        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa vai trò tùy chỉnh không có người dùng thành công")
    void deleteRole_whenRoleCanBeDeleted_shouldDeleteSuccessfully() {
        when(roleRepository.findById(10)).thenReturn(Optional.of(customRole));
        when(accountRepository.countByRoleId(10)).thenReturn(0L);

        roleManagementService.deleteRole(10);

        verify(roleRepository, times(1)).delete(customRole);
    }

    @Test
    @DisplayName("Lấy danh sách quyền của người dùng đang đăng nhập")
    void getUserPermissions_whenAccountExists_shouldReturnPermissionsList() {
        Account account = Account.builder()
                .id(1)
                .username("hr")
                .userId(2)
                .role(hrRole)
                .build();

        when(accountRepository.findByUsername("hr")).thenReturn(Optional.of(account));
        when(roleRepository.findByIdWithPermissions(2)).thenReturn(Optional.of(hrRole));

        UserPermissionsResponse response = roleManagementService.getUserPermissions("hr");

        assertNotNull(response);
        assertEquals(2, response.getUserId());
        assertEquals("hr", response.getUsername());
        assertEquals("HR", response.getRole());
        assertEquals(2, response.getPermissions().size());
        assertTrue(response.getPermissions().contains("USER_VIEW"));
        assertTrue(response.getPermissions().contains("INTERN_APPROVE"));
    }
}
