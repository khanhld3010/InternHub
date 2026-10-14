package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestReportRevisionRequest {

    @NotBlank(message = "Lý do yêu cầu chỉnh sửa lại báo cáo không được để trống")
    private String revisionNote;
}
