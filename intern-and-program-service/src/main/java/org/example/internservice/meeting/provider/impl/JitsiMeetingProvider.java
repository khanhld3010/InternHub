package org.example.internservice.meeting.provider.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.internservice.meeting.entity.MeetingSchedule;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingProviderType;
import org.example.internservice.meeting.provider.MeetingProvider;
import org.example.internservice.meeting.provider.MeetingRoomInfo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Jitsi Meeting Provider: Tự sinh link phòng họp tức thì không cần setup hay API Key (Zero-Setup)
 */
@Component
@Order(3)
@Slf4j
public class JitsiMeetingProvider implements MeetingProvider {

    private static final String JITSI_BASE_URL = "https://meet.jit.si/internhub-meeting-";

    @Override
    public MeetingRoomInfo createMeetingRoom(MeetingSchedule meeting, String requestedLocationOrUrl) {
        String roomId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String joinUrl = JITSI_BASE_URL + roomId;

        log.info("JitsiProvider: Tu dong sinh phong hop Jitsi moi cho meeting: {}", joinUrl);
        return MeetingRoomInfo.builder()
                .provider(MeetingProviderType.JITSI)
                .joinUrl(joinUrl)
                .externalEventId(roomId)
                .note("Phòng họp trực tuyến Jitsi Meet miễn phí (Zero-setup)")
                .build();
    }

    @Override
    public MeetingRoomInfo updateMeetingRoom(MeetingSchedule meeting, String requestedLocationOrUrl) {
        String joinUrl = meeting.getJoinUrl() != null ? meeting.getJoinUrl() : (JITSI_BASE_URL + UUID.randomUUID().toString().substring(0, 8));
        return MeetingRoomInfo.builder()
                .provider(MeetingProviderType.JITSI)
                .joinUrl(joinUrl)
                .externalEventId(meeting.getExternalEventId())
                .note("Phòng họp trực tuyến Jitsi Meet")
                .build();
    }

    @Override
    public void cancelMeetingRoom(MeetingSchedule meeting) {
        log.info("JitsiProvider: Khong can goi API ngoai vi khi huy phong Jitsi: {}", meeting.getJoinUrl());
    }

    @Override
    public boolean supports(MeetingProviderType type) {
        return type == MeetingProviderType.JITSI;
    }
}
