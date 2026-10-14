package org.example.internservice.meeting.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.meeting.client.MeetingEmailClient;
import org.example.internservice.meeting.client.MeetingEmailClient.SendMeetingEmailPayload;
import org.example.internservice.meeting.entity.MeetingSchedule;
import org.example.internservice.meeting.repository.MeetingScheduleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingReminderScheduler {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");

    private final MeetingScheduleRepository meetingScheduleRepository;
    private final MeetingEmailClient meetingEmailClient;
    private final NotificationEventDispatcher notificationEventDispatcher;

    /**
     * Chạy định kỳ mỗi phút để quét các cuộc họp sắp diễn ra trong khoảng 15 phút tới.
     * Sử dụng câu lệnh UPDATE nguyên tử (markReminderSentAtomically) để đảm bảo không bị gửi đúp khi chạy nhiều replica.
     */
    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    public void scanAndSendUpcomingMeetingReminders() {
        LocalDateTime now = LocalDateTime.now();
        // Quét cuộc họp bắt đầu trong khoảng từ now + 14 phút đến now + 16 phút
        LocalDateTime windowStart = now.plusMinutes(14);
        LocalDateTime windowEnd = now.plusMinutes(16);

        List<MeetingSchedule> upcomingMeetings = meetingScheduleRepository.findUpcomingMeetingsForReminder(windowStart, windowEnd);
        if (upcomingMeetings.isEmpty()) {
            return;
        }

        log.info("Tim thay {} cuoc hop sap dien ra can gui nhac nho", upcomingMeetings.size());

        for (MeetingSchedule meeting : upcomingMeetings) {
            // Atomic update check: nếu 1 node khác đã update trước thì rowsUpdated = 0
            int rowsUpdated = meetingScheduleRepository.markReminderSentAtomically(meeting.getId());
            if (rowsUpdated > 0) {
                log.info("Gui nhac nho cuoc hop ID={}, title={}", meeting.getId(), meeting.getTitle());

                // 1. Gửi thông báo WebSocket STOMP thời gian thực (Cả Google Meet, Jitsi, và Link tay đều nhận)
                String formattedTime = meeting.getStartTime() != null ? meeting.getStartTime().format(TIME_FORMAT) : "";
                CreateNotificationInternalRequest notifRequest = CreateNotificationInternalRequest.builder()
                        .recipientId(meeting.getInternUserId())
                        .actorId(meeting.getCreatorId())
                        .title("Nhắc nhở lịch họp: " + meeting.getTitle())
                        .content(String.format("Cuộc họp của bạn sẽ bắt đầu sau khoảng 15 phút (lúc %s). Vui lòng chuẩn bị tham gia!", formattedTime))
                        .type("MEETING_REMINDER")
                        .referenceType("MEETING")
                        .referenceId(String.valueOf(meeting.getId()))
                        .actionUrl(meeting.getJoinUrl() != null && !meeting.getJoinUrl().isBlank() ? meeting.getJoinUrl() : "/intern/dashboard")
                        .build();
                notificationEventDispatcher.dispatch(notifRequest);

                // 2. Gửi Email nhắc nhở:
                // Theo ADR 0002: Với GOOGLE_MEET, Google Calendar / mobile notification đã tự động nhắc lịch,
                // do đó chỉ gửi email reminder cho các nhà cung cấp khác (JITSI, CUSTOM_MANUAL).
                boolean isGoogleMeet = meeting.getProvider() != null && "GOOGLE_MEET".equalsIgnoreCase(meeting.getProvider().name());
                if (!isGoogleMeet) {
                    SendMeetingEmailPayload emailPayload = SendMeetingEmailPayload.builder()
                            .idempotencyKey(UUID.randomUUID().toString())
                            .meetingId(meeting.getId())
                            .title(meeting.getTitle())
                            .description(meeting.getDescription())
                            .meetingType(meeting.getMeetingType() != null ? meeting.getMeetingType().name() : null)
                            .locationOrUrl(meeting.getLocationOrUrl())
                            .startTime(meeting.getStartTime())
                            .endTime(meeting.getEndTime())
                            .organizerName(meeting.getCreatorName())
                            .organizerRole(meeting.getCreatorRole())
                            .internName(meeting.getInternName())
                            .internEmail(meeting.getInternEmail())
                            .actionType("REMINDER")
                            .provider(meeting.getProvider() != null ? meeting.getProvider().name() : null)
                            .sequenceNumber(meeting.getSequenceNumber())
                            .build();

                    meetingEmailClient.sendMeetingEmailAsync(emailPayload);
                }
            }
        }
    }

    /**
     * Tự động quét và đánh dấu COMPLETED cho các cuộc họp đã kết thúc (quá endTime hoặc quá 2 tiếng kể từ startTime)
     * Chạy định kỳ mỗi 5 phút.
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    public void scanAndCompleteExpiredMeetings() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fallbackCutoff = now.minusHours(2);
        int completedCount = meetingScheduleRepository.autoCompleteExpiredMeetings(now, fallbackCutoff);
        if (completedCount > 0) {
            log.info("CronJob: Tu dong hoan thanh {} cuoc hop da qua thoi gian quy dinh", completedCount);
        }
    }
}
