package org.example.internservice.attendance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmForgottenCheckoutRequest {

    @NotNull(message = "Giờ ra thực tế không được để trống")
    private LocalTime actualCheckOutTime;

    @Size(max = 255, message = "Ghi chú xác nhận không được vượt quá 255 ký tự")
    private String confirmationNote;
}
