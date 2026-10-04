package org.example.internservice.mission.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.mission.entity.enums.MissionItemStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateKanbanStatusRequest {

    @NotNull(message = "Trạng thái công việc không được để trống")
    private MissionItemStatus status;

    @Pattern(regexp = "^(https?://).*", message = "Liên kết nộp bài phải là đường dẫn URL hợp lệ bắt đầu bằng http:// hoặc https://")
    @Size(max = 500, message = "Liên kết nộp bài không được vượt quá 500 ký tự")
    private String submissionUrl;

    @Size(max = 2000, message = "Ghi chú hoàn thành không được vượt quá 2000 ký tự")
    private String completionNote;
}
