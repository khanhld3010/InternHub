package org.example.reportingservice.email.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class SendMentorOnboardingEmailRequest {

    private String idempotencyKey;

    @NotNull(message = "mentorProfileId không được để trống")
    private Long mentorProfileId;

    @NotBlank(message = "Email người nhận không được để trống")
    @Email(message = "Email không đúng định dạng hợp lệ")
    private String email;

    @NotBlank(message = "Họ và tên người hướng dẫn không được để trống")
    private String fullName;

    private String departmentName;

    @NotBlank(message = "Token kích hoạt không được để trống")
    private String onboardingToken;
}
