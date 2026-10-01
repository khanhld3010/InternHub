package org.example.employeeservice.rbac.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class UpdateRoleRequest {

    @Size(min = 2, max = 50, message = "Tên vai trò phải từ 2 đến 50 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Tên vai trò chỉ chứa chữ cái, số và dấu gạch dưới")
    private String name;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    private String description;

    @Builder.Default
    private List<String> permissionCodes = new ArrayList<>();
}
