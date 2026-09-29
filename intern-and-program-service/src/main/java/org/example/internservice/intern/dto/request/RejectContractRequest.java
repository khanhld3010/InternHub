package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectContractRequest {

    @NotBlank(message = "Lý do từ chối hợp đồng không được để trống")
    @Size(min = 10, max = 1000, message = "Lý do từ chối phải từ 10 đến 1000 ký tự")
    private String rejectionReason;
}
