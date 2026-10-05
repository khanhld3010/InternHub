package org.example.internservice.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class CreateMissionBoardRequest {

    private Long programId;

    @NotBlank(message = "Tiêu đề bảng nhiệm vụ không được để trống")
    @Size(min = 3, max = 200, message = "Tiêu đề bảng nhiệm vụ phải từ 3 đến 200 ký tự")
    private String title;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;
}
