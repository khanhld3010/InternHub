package org.example.internservice.leave.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Yêu cầu từ chối đơn xin nghỉ phép (TM-28)")
public class RejectLeaveRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    @Size(min = 5, max = 500, message = "Lý do từ chối phải từ 5 đến 500 ký tự")
    @Schema(description = "Lý do bắt buộc khi từ chối đơn xin nghỉ phép", example = "Ngày 15/10 nhóm có buổi Demo quan trọng, em vui lòng dời lịch thi.")
    private String rejectionReason;
}
