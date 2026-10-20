package org.example.internservice.attendance.dto.request;

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
public class AttendanceAppealRequest {

    @NotBlank(message = "Lý do giải trình không được để trống")
    @Size(max = 1000, message = "Lý do giải trình không được vượt quá 1000 ký tự")
    private String reason;

    @Size(max = 500, message = "Đường dẫn tài liệu / ảnh minh chứng không được vượt quá 500 ký tự")
    private String evidenceUrl;
}
