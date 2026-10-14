package org.example.internservice.meeting.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingProviderType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingRoomInfo {
    private MeetingProviderType provider;
    private String joinUrl;
    private String externalEventId;
    private String note;
}
