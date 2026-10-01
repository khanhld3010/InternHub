package org.example.employeeservice.rbac.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionModuleGroupResponse {

    private String module;
    private String moduleName;

    @Builder.Default
    private List<PermissionResponse> permissions = new ArrayList<>();
}
