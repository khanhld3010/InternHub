package org.example.reportingservice.email.dto.request;

import jakarta.validation.constraints.Email;
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
public class SendAccountActivationEmailRequest {

    private String idempotencyKey;

    @NotBlank(message = "Email người nhận không được để trống")
    @Email(message = "Email không đúng định dạng hợp lệ")
    private String email;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @NotBlank(message = "Mã kích hoạt không được để trống")
    private String activationKey;

    @Builder.Default
    private Integer expiresInMinutes = 15;
}
