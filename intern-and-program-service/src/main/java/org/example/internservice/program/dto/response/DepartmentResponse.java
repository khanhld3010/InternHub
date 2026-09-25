package org.example.internservice.program.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.program.entity.Department;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResponse {

    private Long id;
    private String name;
    private String code;
    private String description;
    private String status;

    public static DepartmentResponse fromEntity(Department dept) {
        if (dept == null) return null;
        return DepartmentResponse.builder()
                .id(dept.getId())
                .name(dept.getName())
                .code(dept.getCode())
                .description(dept.getDescription())
                .status(dept.getStatus())
                .build();
    }
}
