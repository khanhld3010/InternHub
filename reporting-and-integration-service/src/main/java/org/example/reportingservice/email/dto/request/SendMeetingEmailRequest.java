package org.example.reportingservice.email.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMeetingEmailRequest {

    private String idempotencyKey;

    @NotNull(message = "Meeting ID khong duoc de trong")
    private Long meetingId;

    @NotBlank(message = "Tieu de cuoc hop khong duoc de trong")
    private String title;

    private String description;

    @NotBlank(message = "Hinh thuc cuoc hop khong duoc de trong (ONLINE hoac OFFLINE)")
    private String meetingType;

    private String locationOrUrl;

    @NotNull(message = "Thoi gian bat dau khong duoc de trong")
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @NotBlank(message = "Ten nguoi to chuc khong duoc de trong")
    private String organizerName;

    private String organizerRole;

    @NotBlank(message = "Ten thuc tap sinh khong duoc de trong")
    private String internName;

    @NotBlank(message = "Email thuc tap sinh khong duoc de trong")
    @Email(message = "Email thuc tap sinh khong hop le")
    private String internEmail;

    /**
     * Loai hanh dong: SCHEDULED, UPDATED, CANCELLED
     */
    @Builder.Default
    private String actionType = "SCHEDULED";

    /**
     * Provider: CUSTOM_MANUAL, GOOGLE_MEET, JITSI
     */
    @Builder.Default
    private String provider = "JITSI";

    /**
     * So thu tu phien ban lich iCalendar (0 = tao moi, >0 = sua, huy)
     */
    @Builder.Default
    private Integer sequenceNumber = 0;
}
