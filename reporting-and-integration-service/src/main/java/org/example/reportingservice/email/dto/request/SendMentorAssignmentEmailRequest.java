package org.example.reportingservice.email.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMentorAssignmentEmailRequest {

    private String idempotencyKey;

    private Long internProfileId;
    private String internCode;
    private String internName;
    private String internEmail;
    private String programName;
    private String appliedPosition;

    @NotBlank(message = "eventType (ASSIGNED/REPLACED/REVOKED) không được để trống")
    private String eventType; // ASSIGNED, REPLACED, REVOKED

    private Long newMentorId;
    private String newMentorName;
    private String newMentorEmail;

    private Long oldMentorId;
    private String oldMentorName;
    private String oldMentorEmail;

    private String reason;
    private String notes;
    private String actorUsername;
}
