package org.example.internservice.meeting.service;

import org.example.internservice.meeting.dto.request.CreateMeetingScheduleRequest;
import org.example.internservice.meeting.dto.request.UpdateMeetingScheduleRequest;
import org.example.internservice.meeting.dto.response.MeetingScheduleResponse;

import java.util.List;

public interface MeetingScheduleService {

    MeetingScheduleResponse createMeeting(Long creatorId, String creatorName, String creatorRole, CreateMeetingScheduleRequest request);

    MeetingScheduleResponse updateMeeting(Long meetingId, Long updaterId, String updaterRole, UpdateMeetingScheduleRequest request);

    MeetingScheduleResponse cancelMeeting(Long meetingId, Long cancellerId, String cancellerRole);

    List<MeetingScheduleResponse> getMyMeetings(Long userId, String role);

    List<MeetingScheduleResponse> getMeetingsByIntern(Long internUserId);

    MeetingScheduleResponse getMeetingById(Long meetingId);
}
