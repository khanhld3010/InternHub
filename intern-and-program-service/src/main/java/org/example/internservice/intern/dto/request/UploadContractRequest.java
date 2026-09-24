package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadContractRequest {

    @NotBlank(message = "Tiêu đề hợp đồng không được để trống")
    @Size(min = 3, max = 200, message = "Tiêu đề hợp đồng phải từ 3 đến 200 ký tự")
    private String contractTitle;

    @NotNull(message = "Ngày bắt đầu hợp đồng không được để trống")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc hợp đồng không được để trống")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Size(max = 50, message = "Mã hợp đồng không được vượt quá 50 ký tự")
    private String contractNumber;

    @DecimalMin(value = "0.0", inclusive = true, message = "Mức phụ cấp không được âm")
    private BigDecimal allowanceAmount;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String notes;
}
