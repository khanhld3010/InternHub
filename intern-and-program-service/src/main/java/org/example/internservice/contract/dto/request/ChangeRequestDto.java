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
public class ChangeRequestDto {

    @NotBlank(message = "Lý do yêu cầu điều chỉnh không được để trống")
    private String reason;
}
