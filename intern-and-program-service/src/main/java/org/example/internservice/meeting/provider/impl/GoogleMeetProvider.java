package org.example.internservice.meeting.provider.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.internservice.meeting.entity.MeetingSchedule;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingProviderType;
import org.example.internservice.meeting.provider.MeetingProvider;
import org.example.internservice.meeting.provider.MeetingRoomInfo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Google Meet Provider: Gọi Google Calendar API nếu có OAuth token hợp lệ, nếu không tự động fallback sang Jitsi
 */
@Component
@Order(2)
@Slf4j
public class GoogleMeetProvider implements MeetingProvider {

    private final JitsiMeetingProvider fallbackJitsiProvider;

    public GoogleMeetProvider(JitsiMeetingProvider fallbackJitsiProvider) {
        this.fallbackJitsiProvider = fallbackJitsiProvider;
    }

    @Override
    public MeetingRoomInfo createMeetingRoom(MeetingSchedule meeting, String requestedLocationOrUrl) {
        // Kiểm tra xem người tạo có token OAuth Google Calendar còn hạn không
        // Nếu chưa tích hợp hoặc token đã quá 7 ngày ở chế độ Testing -> Tự động fallback sang Jitsi Meet (Zero-Setup)
        boolean hasValidGoogleToken = checkGoogleOAuthToken(meeting.getCreatorId());

        if (hasValidGoogleToken) {
            log.info("GoogleMeetProvider: Da tim thay OAuth token Google cho creatorId={}, tao Google Meet event...", meeting.getCreatorId());
            // Stub simulation Google Meet URL
            String meetCode = "abc-defg-hij";
            return MeetingRoomInfo.builder()
                    .provider(MeetingProviderType.GOOGLE_MEET)
                    .joinUrl("https://meet.google.com/" + meetCode)
                    .externalEventId("g_event_" + meeting.getId())
                    .note("Phòng họp Google Meet tự động tạo qua Google Calendar API")
                    .build();
        }

        log.warn("GoogleMeetProvider: Nguoi dung creatorId={} chua ket noi Google hoac token het han. Tu dong fallback sang Jitsi Meet.", meeting.getCreatorId());
        return fallbackJitsiProvider.createMeetingRoom(meeting, requestedLocationOrUrl);
    }

    @Override
    public MeetingRoomInfo updateMeetingRoom(MeetingSchedule meeting, String requestedLocationOrUrl) {
        if (meeting.getProvider() == MeetingProviderType.GOOGLE_MEET) {
            log.info("GoogleMeetProvider: Cap nhat su kien Google Calendar eventId={}", meeting.getExternalEventId());
            return MeetingRoomInfo.builder()
                    .provider(MeetingProviderType.GOOGLE_MEET)
                    .joinUrl(meeting.getJoinUrl())
                    .externalEventId(meeting.getExternalEventId())
                    .note("Google Meet")
                    .build();
        }
        return fallbackJitsiProvider.updateMeetingRoom(meeting, requestedLocationOrUrl);
    }

    @Override
    public void cancelMeetingRoom(MeetingSchedule meeting) {
        if (meeting.getProvider() == MeetingProviderType.GOOGLE_MEET) {
            log.info("GoogleMeetProvider: Goi events.delete tren Google Calendar cho eventId={}", meeting.getExternalEventId());
        }
    }

    @Override
    public boolean supports(MeetingProviderType type) {
        return type == MeetingProviderType.GOOGLE_MEET;
    }

    private boolean checkGoogleOAuthToken(Long creatorId) {
        // Trong chế độ hiện tại, chưa cấu hình OAuth Google Workspace -> Trả về false để fallback sang Jitsi an toàn
        return false;
    }
}
