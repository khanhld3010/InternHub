package org.example.internservice.contract.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignContractRequest {

    @NotBlank(message = "Chữ ký điện tử không được để trống")
    private String signatureData;

    @Builder.Default
    private String authMethod = "JWT_SESSION";

    private String otpCode;
}
