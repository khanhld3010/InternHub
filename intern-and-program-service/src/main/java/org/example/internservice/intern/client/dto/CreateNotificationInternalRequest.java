package org.example.internservice.intern.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationInternalRequest {
    private Long recipientId;
    private Long actorId;
    private String title;
    private String content;
    private String type;
    private String referenceType;
    private String referenceId;
    private String actionUrl;
}
