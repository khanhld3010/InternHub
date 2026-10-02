package org.example.internservice.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.mission.entity.enums.MissionPriority;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMissionItemRequest {

    @NotBlank(message = "Tiêu đề công việc không được để trống")
    @Size(min = 3, max = 200, message = "Tiêu đề công việc phải từ 3 đến 200 ký tự")
    private String title;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    @Builder.Default
    private MissionPriority priority = MissionPriority.MEDIUM;

    private LocalDate dueDate;

    @com.fasterxml.jackson.annotation.JsonAlias({"assigneeInternIds", "assigneeIds"})
    @NotEmpty(message = "Vui lòng chọn ít nhất 1 thực tập sinh tham gia công việc")
    private Set<Long> internIds;
}
