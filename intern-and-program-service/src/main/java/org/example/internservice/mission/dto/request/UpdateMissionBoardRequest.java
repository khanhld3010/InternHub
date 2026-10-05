package org.example.internservice.mission.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.mission.entity.enums.BoardStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMissionBoardRequest {

    @Size(min = 3, max = 200, message = "Tiêu đề bảng nhiệm vụ phải từ 3 đến 200 ký tự")
    private String title;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    private BoardStatus status;
}
