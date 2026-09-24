package org.example.reportingservice.email.dto.response;

import lombok.*;
import org.example.reportingservice.email.entity.EmailStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailStatusResponse {

    private Long id;
    private Long referenceId;
    private String recipientEmail;
    private EmailStatus status;
    private String errorMessage;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
