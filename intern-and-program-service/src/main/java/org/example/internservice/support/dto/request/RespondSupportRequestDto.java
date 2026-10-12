package org.example.internservice.support.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.support.entity.enums.SupportRequestStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespondSupportRequestDto {

    @NotNull(message = "Trạng thái phản hồi là bắt buộc")
    private SupportRequestStatus status; // RESOLVED hoặc REJECTED

    private String hrResponse;

    private String overrideReason; // Nếu duyệt ngoại lệ
}
