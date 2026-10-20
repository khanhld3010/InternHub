package org.example.aiservice.modules.interview.dto.response;

import lombok.*;
import org.example.aiservice.modules.interview.enums.SessionStatus;
import org.example.aiservice.modules.interview.enums.SubmissionReason;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitInterviewSessionResponse {

    private Long sessionId;
    private SessionStatus status;
    private LocalDateTime submittedAt;
    private SubmissionReason submissionReason;
    private String message;
}
