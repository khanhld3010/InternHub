package org.example.internservice.mission.dto.request;

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
public class UpdateMissionItemRequest {

    @Size(min = 3, max = 200, message = "Tiêu đề công việc phải từ 3 đến 200 ký tự")
    private String title;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    private MissionPriority priority;

    private LocalDate dueDate;

    private Set<Long> internIds;
}
