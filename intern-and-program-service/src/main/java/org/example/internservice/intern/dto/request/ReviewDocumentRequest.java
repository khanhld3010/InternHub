package org.example.internservice.intern.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.intern.entity.enums.DocumentStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request xét duyệt tài liệu của thực tập sinh (Phê duyệt hoặc Từ chối)")
public class ReviewDocumentRequest {

    @NotNull(message = "Trạng thái xét duyệt không được để trống")
    @Schema(description = "Trạng thái xét duyệt mới", example = "APPROVED", allowableValues = {"APPROVED", "REJECTED"})
    private DocumentStatus status;

    @Schema(description = "Lý do từ chối (bắt buộc nếu status = REJECTED)", example = "CV thiếu thông tin liên hệ và số điện thoại")
    private String rejectionReason;
}
