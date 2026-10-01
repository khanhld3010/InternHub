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
public class UserPermissionsResponse {

    private Integer userId;
    private String username;
    private String role;

    @Builder.Default
    private List<String> permissions = new ArrayList<>();
}
