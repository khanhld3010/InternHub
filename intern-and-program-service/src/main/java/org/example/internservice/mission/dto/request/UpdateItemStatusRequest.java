package org.example.internservice.mission.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class UpdateItemStatusRequest {

    @NotNull(message = "Trạng thái công việc không được để trống")
    private MissionItemStatus status;

    private String submissionUrl;

    private String completionNote;
}
