package org.example.employeeservice.rbac.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleResponse {

    private Integer id;
    private String name;
    private String description;
    private Boolean isSystem;
    private Long userCount;
    private Integer permissionCount;
    private LocalDateTime createdAt;
}
