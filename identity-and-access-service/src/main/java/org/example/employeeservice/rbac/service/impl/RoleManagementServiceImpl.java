package org.example.employeeservice.rbac.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.example.employeeservice.rbac.service.RoleManagementService;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.PermissionRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleManagementServiceImpl implements RoleManagementService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AccountRepository accountRepository;

    @Override
    public List<RoleResponse> getAllRoles() {
        log.info("Lấy danh sách toàn bộ vai trò kèm số lượng user và permission");
        List<Role> roles = roleRepository.findAllWithPermissions();

        return roles.stream().map(role -> {
            long userCount = accountRepository.countByRoleId(role.getId());
            int permCount = (role.getPermissions() != null) ? role.getPermissions().size() : 0;

            return RoleResponse.builder()
                    .id(role.getId())
                    .name(role.getName())
                    .description(role.getDescription())
                    .isSystem(Boolean.TRUE.equals(role.getIsSystem()))
                    .userCount(userCount)
                    .permissionCount(permCount)
                    .createdAt(role.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public RoleDetailResponse getRoleById(Integer id) {
        log.info("Lấy thông tin chi tiết vai trò ID={}", id);
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với ID: " + id));

        return mapToDetailResponse(role);
    }

    @Override
    @Transactional
    public RoleDetailResponse createRole(CreateRoleRequest request) {
        String cleanName = request.getName().trim().toUpperCase();
        if (cleanName.startsWith("ROLE_")) {
            cleanName = cleanName.substring(5);
        }

        log.info("Tiến hành tạo mới vai trò: {}", cleanName);

        if (roleRepository.existsByNameIgnoreCase(cleanName)) {
            throw new DuplicateResourceException("Vai trò '" + cleanName + "' đã tồn tại trong hệ thống");
        }

        Set<Permission> permissions = resolvePermissions(request.getPermissionCodes());

        Role newRole = Role.builder()
                .name(cleanName)
                .description(request.getDescription())
                .isSystem(false)
                .permissions(permissions)
                .build();

        Role saved = roleRepository.save(newRole);
        log.info("Đã tạo thành công vai trò: {} [ID={}]", saved.getName(), saved.getId());
        return mapToDetailResponse(saved);
    }

    @Override
    @Transactional
    public RoleDetailResponse updateRole(Integer id, UpdateRoleRequest request) {
        log.info("Cập nhật vai trò ID={}", id);
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với ID: " + id));

        boolean isSystemRole = Boolean.TRUE.equals(role.getIsSystem());

        if (StringUtils.hasText(request.getName())) {
            String cleanName = request.getName().trim().toUpperCase();
            if (cleanName.startsWith("ROLE_")) {
                cleanName = cleanName.substring(5);
            }

            if (!cleanName.equalsIgnoreCase(role.getName())) {
                if (isSystemRole) {
                    throw new BadRequestException("Không thể thay đổi tên của vai trò mặc định hệ thống: " + role.getName());
                }
                if (roleRepository.existsByNameIgnoreCase(cleanName)) {
                    throw new DuplicateResourceException("Tên vai trò '" + cleanName + "' đã được sử dụng");
                }
                role.setName(cleanName);
            }
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getPermissionCodes() != null) {
            Set<Permission> newPermissions = resolvePermissions(request.getPermissionCodes());
            role.getPermissions().clear();
            role.getPermissions().addAll(newPermissions);
        }

        Role updated = roleRepository.save(role);
        log.info("Đã cập nhật thành công vai trò ID={}", updated.getId());
        return mapToDetailResponse(updated);
    }

    @Override
    @Transactional
    public void deleteRole(Integer id) {
        log.info("Xử lý xóa vai trò ID={}", id);
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với ID: " + id));

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BadRequestException("Không thể xóa vai trò mặc định của hệ thống: " + role.getName());
        }

        long assignedUsers = accountRepository.countByRoleId(id);
        if (assignedUsers > 0) {
            throw new BadRequestException("Không thể xóa vai trò đang có " + assignedUsers + " người dùng sử dụng. Vui lòng chuyển vai trò cho các tài khoản liên quan trước.");
        }

        roleRepository.delete(role);
        log.info("Đã xóa hoàn tất vai trò [ID={}, Name={}]", id, role.getName());
    }

    @Override
    public UserPermissionsResponse getUserPermissions(String username) {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với username: " + username));

        if (account.getRole() == null) {
            return UserPermissionsResponse.builder()
                    .userId(account.getUserId())
                    .username(account.getUsername())
                    .role("USER")
                    .permissions(Collections.emptyList())
                    .build();
        }

        Role role = roleRepository.findByIdWithPermissions(account.getRole().getId())
                .orElse(account.getRole());

        List<String> permissionCodes = (role.getPermissions() != null)
                ? role.getPermissions().stream().map(Permission::getCode).sorted().collect(Collectors.toList())
                : Collections.emptyList();

        return UserPermissionsResponse.builder()
                .userId(account.getUserId())
                .username(account.getUsername())
                .role(role.getName().toUpperCase())
                .permissions(permissionCodes)
                .build();
    }

    private Set<Permission> resolvePermissions(List<String> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> uniqueCodes = requestedCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        Set<Permission> foundPermissions = permissionRepository.findAllByCodeIn(uniqueCodes);
        Set<String> foundCodes = foundPermissions.stream().map(Permission::getCode).collect(Collectors.toSet());

        Set<String> invalidCodes = uniqueCodes.stream()
                .filter(code -> !foundCodes.contains(code))
                .collect(Collectors.toSet());

        if (!invalidCodes.isEmpty()) {
            throw new BadRequestException("Danh sách quyền chứa các mã không hợp lệ: " + String.join(", ", invalidCodes));
        }

        return foundPermissions;
    }

    private RoleDetailResponse mapToDetailResponse(Role role) {
        long userCount = accountRepository.countByRoleId(role.getId());
        List<String> permCodes = (role.getPermissions() != null)
                ? role.getPermissions().stream().map(Permission::getCode).sorted().collect(Collectors.toList())
                : new ArrayList<>();

        return RoleDetailResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isSystem(Boolean.TRUE.equals(role.getIsSystem()))
                .userCount(userCount)
                .permissions(permCodes)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
