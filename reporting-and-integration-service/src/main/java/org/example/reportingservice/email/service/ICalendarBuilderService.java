package org.example.reportingservice.email.service;

import lombok.extern.slf4j.Slf4j;
import org.example.reportingservice.email.dto.request.SendMeetingEmailRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Service sinh nội dung tệp đính kèm .ics (chuẩn RFC 5545).
 * Sử dụng format iCalendar thuần RFC 5545 để đảm bảo 100% tương thích với mọi Calendar Client
 * (Google Calendar, Apple Calendar, Microsoft Outlook) mà không phụ thuộc vào API breaking changes của thư viện ngoài.
 */
@Service
@Slf4j
public class ICalendarBuilderService {

    private static final String PROD_ID = "-//InternHub//Meeting Calendar 1.0//VI";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    public byte[] generateIcsBytes(SendMeetingEmailRequest request) {
        try {
            String icsContent = buildIcsContent(request);
            return icsContent.getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Lỗi sinh file iCalendar .ics cho meetingId={}: {}", request.getMeetingId(), ex.getMessage(), ex);
            return buildIcsContent(request).getBytes(StandardCharsets.UTF_8);
        }
    }

    private String buildIcsContent(SendMeetingEmailRequest request) {
        String actionType = request.getActionType() != null ? request.getActionType().toUpperCase() : "SCHEDULED";
        String status = "CANCELLED".equals(actionType) ? "CANCELLED" : "CONFIRMED";
        String method = "CANCELLED".equals(actionType) ? "CANCEL" : "REQUEST";
        int seq = request.getSequenceNumber() != null ? request.getSequenceNumber() : 0;

        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = request.getEndTime() != null ? request.getEndTime() : startTime.plusHours(1);

        // Chuyển sang định dạng UTC timestamp (Z)
        String dtStart = startTime.atZone(VIETNAM_ZONE).withZoneSameInstant(ZoneId.of("UTC")).format(UTC_FORMATTER);
        String dtEnd = endTime.atZone(VIETNAM_ZONE).withZoneSameInstant(ZoneId.of("UTC")).format(UTC_FORMATTER);
        String dtStamp = LocalDateTime.now().atZone(VIETNAM_ZONE).withZoneSameInstant(ZoneId.of("UTC")).format(UTC_FORMATTER);

        String uid = String.format("meeting-%d@internhub.vn", request.getMeetingId());
        String title = escapeIcsText(request.getTitle());
        String description = escapeIcsText(request.getDescription() != null ? request.getDescription() : "Cuộc họp trao đổi tiến độ thực tập tại InternHub");
        String location = escapeIcsText(request.getLocationOrUrl() != null ? request.getLocationOrUrl() : "");

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("PRODID:").append(PROD_ID).append("\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:").append(method).append("\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:").append(uid).append("\r\n");
        sb.append("DTSTAMP:").append(dtStamp).append("\r\n");
        sb.append("DTSTART:").append(dtStart).append("\r\n");
        sb.append("DTEND:").append(dtEnd).append("\r\n");
        sb.append("SUMMARY:").append(title).append("\r\n");
        if (!description.isEmpty()) {
            sb.append("DESCRIPTION:").append(description).append("\r\n");
        }
        if (!location.isEmpty()) {
            sb.append("LOCATION:").append(location).append("\r\n");
        }
        sb.append("SEQUENCE:").append(seq).append("\r\n");
        sb.append("STATUS:").append(status).append("\r\n");
        sb.append("ORGANIZER;CN=InternHub:mailto:notifications@internhub.vn\r\n");
        if (request.getInternEmail() != null && !request.getInternEmail().isBlank()) {
            sb.append("ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:").append(request.getInternEmail()).append("\r\n");
        }
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");

        return sb.toString();
    }

    private String escapeIcsText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace(";", "\\;")
                   .replace(",", "\\,")
                   .replace("\r\n", "\\n")
                   .replace("\n", "\\n");
    }
}
