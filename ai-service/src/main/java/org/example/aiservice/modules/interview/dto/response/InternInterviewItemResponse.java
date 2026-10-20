package org.example.aiservice.modules.interview.dto.response;

import lombok.*;
import org.example.aiservice.modules.interview.enums.InterviewMode;
import org.example.aiservice.modules.interview.enums.SessionStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternInterviewItemResponse {

    private Long sessionId;
    private Long scheduleId;
    private String interviewTitle;
    private SessionStatus status;
    private InterviewMode interviewMode;
    private Integer totalQuestions;
    private Integer durationPerQuestionSec;
    private Integer totalDurationMinutes;
    private LocalDateTime expireAt;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private String directRoomToken;
}
