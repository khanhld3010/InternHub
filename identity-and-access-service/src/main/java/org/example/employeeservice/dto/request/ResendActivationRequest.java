package org.example.employeeservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResendActivationRequest {

    @NotBlank(message = "Tên đăng nhập hoặc email không được để trống")
    private String identifier;
}
