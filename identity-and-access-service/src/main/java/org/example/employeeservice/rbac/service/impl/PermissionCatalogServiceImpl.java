package org.example.employeeservice.rbac.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.entity.Permission;
import org.example.employeeservice.rbac.dto.response.PermissionModuleGroupResponse;
import org.example.employeeservice.rbac.dto.response.PermissionResponse;
import org.example.employeeservice.rbac.service.PermissionCatalogService;
import org.example.employeeservice.repository.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionCatalogServiceImpl implements PermissionCatalogService {

    private final PermissionRepository permissionRepository;

    private static final Map<String, String> MODULE_DISPLAY_NAMES = Map.of(
            "USER", "Quản Lý Người Dùng & Tài Khoản",
            "ROLE", "Quản Lý Vai Trò & Phân Quyền",
            "INTERN", "Quản Lý Hồ Sơ Thực Tập Sinh",
            "MENTOR", "Quản Lý Đội Ngũ Người Hướng Dẫn",
            "PROGRAM", "Quản Lý Chương Trình Thực Tập",
            "CONTRACT", "Quản Lý Hợp Đồng & Đãi Ngộ",
            "DOCUMENT", "Quản Lý Tài Liệu & Hồ Sơ Đính Kèm",
            "PROFILE", "Hồ Sơ Cá Nhân & Tài Khoản",
            "SYSTEM", "Quản Trị Hệ Thống, Sao Lưu & Nhật Ký",
            "REPORT", "Báo Cáo & Thống Kê"
    );

    @Override
    public List<PermissionModuleGroupResponse> getAllPermissionsGroupedByModule() {
        log.info("Truy vấn danh mục đặc quyền gom theo Module");
        List<Permission> permissions = permissionRepository.findAllByOrderByModuleAscCodeAsc();

        Map<String, List<PermissionResponse>> groupedMap = permissions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.groupingBy(
                        PermissionResponse::getModule,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<PermissionModuleGroupResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<PermissionResponse>> entry : groupedMap.entrySet()) {
            String moduleKey = entry.getKey();
            String displayName = MODULE_DISPLAY_NAMES.getOrDefault(moduleKey, moduleKey);

            result.add(PermissionModuleGroupResponse.builder()
                    .module(moduleKey)
                    .moduleName(displayName)
                    .permissions(entry.getValue())
                    .build());
        }

        return result;
    }

    @Override
    public List<PermissionResponse> getAllPermissions() {
        log.info("Truy vấn toàn bộ danh sách đặc quyền");
        return permissionRepository.findAllByOrderByModuleAscCodeAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private PermissionResponse mapToResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .name(permission.getName())
                .module(permission.getModule())
                .description(permission.getDescription())
                .build();
    }
}
