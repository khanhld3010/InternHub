package org.example.reportingservice.email.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendInternDecisionEmailRequest {

    private String idempotencyKey;

    @NotNull(message = "internProfileId không được để trống")
    private Long internProfileId;

    private String internCode;

    @NotBlank(message = "Họ tên ứng viên không được để trống")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Quyết định (APPROVED/REJECTED) không được để trống")
    private String decision;

    private String rejectionReason;

    private String appliedPosition;

    private LocalDate startDate;

    private String department;

    private String mentorName;

    private String onboardingToken;
}
