package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.intern.entity.enums.InternStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternDecisionRequest {

    @NotNull(message = "Quyết định xét duyệt không được để trống")
    private InternStatus decision;

    @Size(max = 1000, message = "Lý do từ chối không được vượt quá 1000 ký tự")
    private String rejectionReason;

    public boolean isValidDecision() {
        return decision == InternStatus.APPROVED || decision == InternStatus.REJECTED;
    }

    public boolean hasValidRejectionReason() {
        return rejectionReason != null && rejectionReason.trim().length() >= 5;
    }

    public String getTrimmedRejectionReason() {
        return rejectionReason != null ? rejectionReason.trim() : null;
    }
}
