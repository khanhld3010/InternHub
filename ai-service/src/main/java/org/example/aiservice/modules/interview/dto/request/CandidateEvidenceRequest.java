package org.example.aiservice.modules.interview.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.aiservice.modules.interview.enums.AuditEventType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateEvidenceRequest {

    @NotNull(message = "Event type must not be null")
    private AuditEventType eventType;

    private String metadata;

    private LocalDateTime clientTimestamp;
}
