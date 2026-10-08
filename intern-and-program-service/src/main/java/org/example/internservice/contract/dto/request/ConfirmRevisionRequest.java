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
public class ConfirmRevisionRequest {

    @NotBlank(message = "Phiên bản điều khoản không được để trống")
    private String consentTextVersion;

    @NotBlank(message = "Nội dung xác nhận cam kết không được để trống")
    private String consentTextSnapshot;
}
