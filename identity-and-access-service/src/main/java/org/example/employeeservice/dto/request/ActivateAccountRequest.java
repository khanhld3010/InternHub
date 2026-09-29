package org.example.employeeservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class ActivateAccountRequest {

    @NotBlank(message = "Tên đăng nhập hoặc email không được để trống")
    private String identifier;

    @NotBlank(message = "Mã kích hoạt không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã kích hoạt phải gồm đúng 6 chữ số")
    private String activationKey;
}
