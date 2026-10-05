package org.example.internservice.leave.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu phê duyệt đơn xin nghỉ phép (TM-28)")
public class ApproveLeaveRequest {

    @Size(max = 500, message = "Ghi chú phê duyệt không được vượt quá 500 ký tự")
    @Schema(description = "Ghi chú hoặc lời dặn của người phê duyệt (tùy chọn)", example = "Đồng ý cho em nghỉ ôn thi. Chúc em thi tốt!")
    private String approvalNote;
}
