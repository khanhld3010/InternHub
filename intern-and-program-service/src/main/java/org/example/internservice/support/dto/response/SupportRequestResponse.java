package org.example.internservice.support.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.support.entity.enums.SupportRequestStatus;
import org.example.internservice.support.entity.enums.SupportRequestType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportRequestResponse {

    private Long id;
    private Long internUserId;
    private Long internProfileId;
    private String internFullName;
    private String internEmail;

    private SupportRequestType requestType;
    private String title;
    private String content;

    private boolean hasAttachment;
    private String attachmentFilename;

    private SupportRequestStatus status;
    private String hrResponse;

    private boolean hasResponseAttachment;
    private String responseFilename;

    private Long handlerId;
    private String handlerName;
    private String overrideReason;

    private LocalDateTime slaDueDate;
    private boolean isOverdueSla;

    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
