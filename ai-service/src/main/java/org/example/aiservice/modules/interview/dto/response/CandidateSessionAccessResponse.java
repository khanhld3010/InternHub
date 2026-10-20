package org.example.aiservice.modules.interview.dto.response;

import lombok.*;
import org.example.aiservice.modules.interview.enums.InterviewMode;
import org.example.aiservice.modules.interview.enums.SessionStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateSessionAccessResponse {

    private String candidateSessionToken;
    private Long sessionId;
    private String candidateName;
    private String candidateEmail;
    private String interviewTitle;
    private SessionStatus status;
    private InterviewMode interviewMode;
    private Integer totalQuestions;
    private Integer durationPerQuestionSec;
    private Integer totalDurationMinutes;
    private Integer maxBlurStrikes;
    private Boolean allowRevisitQuestions;
    private Boolean autoSubmitOnExceed;
}
