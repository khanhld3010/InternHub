package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TerminateContractRequest {

    @NotBlank(message = "Lý do chấm dứt hợp đồng không được để trống")
    @Size(min = 5, max = 1000, message = "Lý do chấm dứt hợp đồng phải từ 5 đến 1000 ký tự")
    private String terminationReason;
}
