package org.example.aiservice.modules.interview.dto.response;

import lombok.*;
import org.example.aiservice.modules.interview.enums.InterviewMode;
import org.example.aiservice.modules.interview.enums.SessionStatus;
import org.example.aiservice.modules.interview.enums.SubmissionReason;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewScheduleDetailResponse {

    private Long scheduleId;
    private String title;
    private Long questionSetId;
    private String questionSetTitle;
    private Long programId;
    private InterviewMode interviewMode;
    private Integer durationPerQuestionSec;
    private Integer totalDurationMinutes;
    private Boolean allowRevisitQuestions;
    private Integer maxBlurStrikes;
    private Boolean autoSubmitOnExceed;
    private LocalDateTime expireAt;
    private String createdByHrId;
    private LocalDateTime createdAt;
    private List<CandidateSessionSummary> sessions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidateSessionSummary {
        private Long sessionId;
        private Long candidateId;
        private String candidateEmail;
        private String candidateName;
        private SessionStatus status;
        private Integer blurCount;
        private Integer pasteCount;
        private LocalDateTime sessionStartedAt;
        private LocalDateTime submittedAt;
        private SubmissionReason submissionReason;
        private String manualInviteLink; // Dùng khi cần copy link gửi thủ công qua Zalo/Phone
    }
}
