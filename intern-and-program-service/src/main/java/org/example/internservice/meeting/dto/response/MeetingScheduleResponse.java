package org.example.internservice.meeting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingStatus;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingScheduleResponse {

    private Long id;
    private String title;
    private String description;
    private MeetingType meetingType;
    private String locationOrUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long creatorId;
    private String creatorName;
    private String creatorRole;
    private Long internProfileId;
    private Long internUserId;
    private String internName;
    private String internEmail;
    private MeetingStatus status;
    private String provider;
    private String joinUrl;
    private String externalEventId;
    private Integer sequenceNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

