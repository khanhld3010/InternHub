package org.example.internservice.meeting.provider.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.internservice.meeting.entity.MeetingSchedule;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingProviderType;
import org.example.internservice.meeting.provider.MeetingProvider;
import org.example.internservice.meeting.provider.MeetingRoomInfo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Manual Meeting Provider: Tôn trọng tuyệt đối đường link do Mentor / HR tự nhập vào (Zoom, Teams, Meet cá nhân, ...)
 */
@Component
@Order(1)
@Slf4j
public class ManualMeetingProvider implements MeetingProvider {

    @Override
    public MeetingRoomInfo createMeetingRoom(MeetingSchedule meeting, String requestedLocationOrUrl) {
        log.info("ManualMeetingProvider: Su dung link hop thu cong do nguoi dung cung cap: {}", requestedLocationOrUrl);
        return MeetingRoomInfo.builder()
                .provider(MeetingProviderType.CUSTOM_MANUAL)
                .joinUrl(requestedLocationOrUrl)
                .externalEventId(null)
                .note("Đường link / địa điểm do Người tổ chức cung cấp")
                .build();
    }

    @Override
    public MeetingRoomInfo updateMeetingRoom(MeetingSchedule meeting, String requestedLocationOrUrl) {
        String finalUrl = (requestedLocationOrUrl != null && !requestedLocationOrUrl.isBlank())
                ? requestedLocationOrUrl
                : meeting.getJoinUrl();
        return MeetingRoomInfo.builder()
                .provider(MeetingProviderType.CUSTOM_MANUAL)
                .joinUrl(finalUrl)
                .externalEventId(null)
                .note("Đường link / địa điểm thủ công đã được cập nhật")
                .build();
    }

    @Override
    public void cancelMeetingRoom(MeetingSchedule meeting) {
        log.info("ManualMeetingProvider: Huy cuoc hop thu cong id={}", meeting.getId());
    }

    @Override
    public boolean supports(MeetingProviderType type) {
        return type == MeetingProviderType.CUSTOM_MANUAL;
    }
}
