package org.example.internservice.allowance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchApproveAllowanceRequest {

    @NotBlank(message = "Kỳ lương không được để trống")
    private String periodMonth;

    private List<Long> recordIds; // Nếu null hoặc rỗng thì duyệt toàn bộ bản ghi DRAFT của kỳ
}
