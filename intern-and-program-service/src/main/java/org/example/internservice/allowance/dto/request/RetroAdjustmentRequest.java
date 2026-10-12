package org.example.internservice.allowance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetroAdjustmentRequest {

    @NotNull(message = "ID của thực tập sinh không được để trống")
    private Long internUserId;

    @NotBlank(message = "Kỳ mục tiêu áp dụng không được để trống")
    private String targetPeriodMonth; // YYYY-MM

    @NotBlank(message = "Kỳ gốc phát sinh sai sót không được để trống")
    private String refPeriodMonth; // YYYY-MM

    @NotNull(message = "Số tiền điều chỉnh bù không được để trống")
    private BigDecimal amount; // Có thể dương (truy lĩnh) hoặc âm (truy thu)

    @NotBlank(message = "Lý do truy lĩnh/truy thu là bắt buộc")
    private String reason;
}
