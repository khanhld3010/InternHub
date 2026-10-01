package org.example.employeeservice.rbac.service;

import org.example.employeeservice.rbac.dto.response.PermissionModuleGroupResponse;
import org.example.employeeservice.rbac.dto.response.PermissionResponse;

import java.util.List;

public interface PermissionCatalogService {

    List<PermissionModuleGroupResponse> getAllPermissionsGroupedByModule();

    List<PermissionResponse> getAllPermissions();
}
