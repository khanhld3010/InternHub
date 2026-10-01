package org.example.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {
    @NotNull(message = "recipientId is required")
    private Long recipientId;

    private Long actorId;

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "content is required")
    private String content;

    @NotBlank(message = "type is required")
    private String type;

    private String referenceType;
    private String referenceId;
    private String actionUrl;
}
