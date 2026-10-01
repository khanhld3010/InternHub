package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long recipientId;
    private Long actorId;
    private String title;
    private String content;
    private String type;
    private String referenceType;
    private String referenceId;
    private String actionUrl;
    private boolean isRead;
    private LocalDateTime createdAt;
}
