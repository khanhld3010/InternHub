package org.example.internservice.meeting.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.meeting.entity.MeetingSchedule;
import org.example.internservice.meeting.entity.MeetingSchedule.MeetingProviderType;
import org.example.internservice.meeting.provider.impl.GoogleMeetProvider;
import org.example.internservice.meeting.provider.impl.JitsiMeetingProvider;
import org.example.internservice.meeting.provider.impl.ManualMeetingProvider;
import org.springframework.stereotype.Service;

/**
 * Điều phối viên Meeting Provider theo Thứ tự ưu tiên đã chốt trong ADR 0002:
 * 1. Link tay thắng tất cả (CUSTOM_MANUAL)
 * 2. Để trống + Có Google -> GOOGLE_MEET
 * 3. Để trống + Chưa có Google / Lỗi -> JITSI (Fallback mặc định)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingProviderResolver {

    private final ManualMeetingProvider manualProvider;
    private final GoogleMeetProvider googleMeetProvider;
    private final JitsiMeetingProvider jitsiProvider;

    public MeetingRoomInfo resolveAndCreate(MeetingSchedule meeting, String requestedUrl) {
        // 1. Uu tien 1: Nguoi dung da tu nhap link
        if (requestedUrl != null && !requestedUrl.isBlank()) {
            log.info("Resolver: Phat hien link tay nguoi dung cung cap -> Chon CUSTOM_MANUAL");
            return manualProvider.createMeetingRoom(meeting, requestedUrl.trim());
        }

        // 2. Uu tien 2: De trong o link -> Thu Google Meet
        log.info("Resolver: O link de trong -> Thu Google Meet provider...");
        MeetingRoomInfo googleResult = googleMeetProvider.createMeetingRoom(meeting, requestedUrl);
        if (googleResult.getProvider() == MeetingProviderType.GOOGLE_MEET) {
            return googleResult;
        }

        // 3. Uu tien 3: Fallback ve Jitsi Meet (Zero-Setup)
        log.info("Resolver: Google Meet khong kha dung -> Fallback sang JITSI Meet");
        return jitsiProvider.createMeetingRoom(meeting, requestedUrl);
    }

    public MeetingRoomInfo resolveAndUpdate(MeetingSchedule meeting, String requestedUrl) {
        if (requestedUrl != null && !requestedUrl.isBlank()) {
            return manualProvider.updateMeetingRoom(meeting, requestedUrl.trim());
        }
        if (meeting.getProvider() == MeetingProviderType.GOOGLE_MEET) {
            return googleMeetProvider.updateMeetingRoom(meeting, requestedUrl);
        }
        return jitsiProvider.updateMeetingRoom(meeting, requestedUrl);
    }

    public void resolveAndCancel(MeetingSchedule meeting) {
        if (meeting.getProvider() == MeetingProviderType.GOOGLE_MEET) {
            googleMeetProvider.cancelMeetingRoom(meeting);
        } else if (meeting.getProvider() == MeetingProviderType.CUSTOM_MANUAL) {
            manualProvider.cancelMeetingRoom(meeting);
        } else {
            jitsiProvider.cancelMeetingRoom(meeting);
        }
    }
}
