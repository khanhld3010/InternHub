package org.example.internservice.allowance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockPeriodRequest {

    @NotBlank(message = "Lý do mở khóa kỳ lương là bắt buộc")
    private String reason;
}
