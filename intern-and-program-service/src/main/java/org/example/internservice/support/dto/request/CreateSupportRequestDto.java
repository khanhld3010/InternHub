package org.example.internservice.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.support.entity.enums.SupportRequestType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSupportRequestDto {

    @NotNull(message = "Loại yêu cầu là bắt buộc")
    private SupportRequestType requestType;

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Nội dung chi tiết không được để trống")
    private String content;
}
