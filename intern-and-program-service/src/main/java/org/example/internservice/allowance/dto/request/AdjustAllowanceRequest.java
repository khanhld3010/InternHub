package org.example.internservice.allowance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
public class AdjustAllowanceRequest {

    @NotNull(message = "Số ngày làm việc thực tế không được để trống")
    @Min(value = 0, message = "Số ngày làm việc không được âm")
    private Integer actualWorkingDays;

    @NotNull(message = "Số ngày nghỉ phép/lễ không được để trống")
    @Min(value = 0, message = "Số ngày nghỉ phép không được âm")
    private Integer paidLeaveDays;

    @NotNull(message = "Tiền thưởng không được để trống")
    @DecimalMin(value = "0.0", message = "Tiền thưởng không được âm")
    private BigDecimal bonusAmount;

    @NotNull(message = "Tiền khấu trừ không được để trống")
    @DecimalMin(value = "0.0", message = "Tiền khấu trừ không được âm")
    private BigDecimal deductionAmount;

    @NotBlank(message = "Lý do điều chỉnh là bắt buộc")
    private String adjustmentReason;

    private String notes;
}
