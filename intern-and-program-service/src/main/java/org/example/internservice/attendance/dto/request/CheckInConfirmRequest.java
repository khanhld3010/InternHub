package org.example.internservice.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class CheckInConfirmRequest {

    @NotBlank(message = "Mã xác thực phiên QR (qrToken) không được để trống")
    private String qrToken;

    @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
    private String notes;
}
