package org.example.employeeservice.rbac.service;

import org.example.employeeservice.rbac.dto.request.CreateRoleRequest;
import org.example.employeeservice.rbac.dto.request.UpdateRoleRequest;
import org.example.employeeservice.rbac.dto.response.RoleDetailResponse;
import org.example.employeeservice.rbac.dto.response.RoleResponse;
import org.example.employeeservice.rbac.dto.response.UserPermissionsResponse;

import java.util.List;

public interface RoleManagementService {

    List<RoleResponse> getAllRoles();

    RoleDetailResponse getRoleById(Integer id);

    RoleDetailResponse createRole(CreateRoleRequest request);

    RoleDetailResponse updateRole(Integer id, UpdateRoleRequest request);

    void deleteRole(Integer id);

    UserPermissionsResponse getUserPermissions(String username);
}
