package org.example.aiservice.modules.interview.dto.response;

import lombok.*;
import org.example.aiservice.modules.interview.enums.InterviewMode;
import org.example.aiservice.modules.interview.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateRecoveryStateResponse {

    private Long sessionId;
    private String interviewTitle;
    private String candidateName;
    private String candidateEmail;
    private SessionStatus status;
    private InterviewMode interviewMode;
    private Integer totalQuestions;
    private Integer currentQuestionIndex;
    private LocalDateTime serverNow;
    private LocalDateTime sessionDeadline;
    private Long remainingSecondsOverall;
    private Integer blurCount;
    private Integer maxBlurStrikes;
    private Boolean allowRevisitQuestions;
    private List<CandidateQuestionResponse> questions;
}
