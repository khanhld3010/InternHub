package org.example.internservice.leave.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.example.internservice.leave.entity.enums.LeaveType;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu nộp đơn xin nghỉ phép của thực tập sinh (TM-28)")
public class CreateLeaveRequest {

    @NotNull(message = "Loại nghỉ phép không được để trống")
    @Schema(description = "Loại nghỉ phép", example = "ACADEMIC_EXAM")
    private LeaveType leaveType;

    @NotNull(message = "Hình thức nghỉ phép không được để trống")
    @Schema(description = "Hình thức nghỉ phép: FULL_DAY, MORNING, AFTERNOON", example = "FULL_DAY")
    private LeaveDurationType durationType;

    @NotNull(message = "Ngày bắt đầu nghỉ không được để trống")
    @Schema(description = "Ngày bắt đầu nghỉ (YYYY-MM-DD)", example = "2026-10-15")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc nghỉ không được để trống")
    @Schema(description = "Ngày kết thúc nghỉ (YYYY-MM-DD)", example = "2026-10-16")
    private LocalDate endDate;

    @NotBlank(message = "Lý do xin nghỉ không được để trống")
    @Size(min = 10, max = 500, message = "Lý do xin nghỉ phải từ 10 đến 500 ký tự")
    @Schema(description = "Lý do chi tiết xin nghỉ phép", example = "Em xin phép nghỉ để tham gia kỳ thi vấn đáp tốt nghiệp tại trường ĐH Bách Khoa.")
    private String reason;

    @Size(max = 500, message = "Đường dẫn minh chứng không được vượt quá 500 ký tự")
    @Schema(description = "URL tài liệu hoặc ảnh minh chứng đính kèm (nếu có)", example = "https://storage.internhub.io/documents/exam_schedule_2026.pdf")
    private String attachmentUrl;
}
