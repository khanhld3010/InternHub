package org.example.internservice.meeting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMeetingScheduleRequest {

    @NotBlank(message = "Tiêu đề cuộc họp không được để trống")
    private String title;

    private String description;

    @NotNull(message = "Hình thức họp không được để trống")
    private MeetingType meetingType;

    private String locationOrUrl;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
