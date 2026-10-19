package org.example.aiservice.modules.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.aiservice.modules.interview.enums.InterviewMode;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInterviewScheduleRequest {

    @NotBlank(message = "Title must not be blank")
    private String title;

    @NotNull(message = "Question set ID is required")
    private Long questionSetId;

    private Long programId;

    @Builder.Default
    private InterviewMode interviewMode = InterviewMode.PER_QUESTION;

    @Builder.Default
    private Integer durationPerQuestionSec = 180;

    private Integer totalDurationMinutes;

    @Builder.Default
    private Boolean allowRevisitQuestions = false;

    @Builder.Default
    private Integer maxBlurStrikes = 3;

    @Builder.Default
    private Boolean autoSubmitOnExceed = true;

    @Builder.Default
    private Integer expireHours = 48; // Mặc định link hết hạn sau 48h

    @NotEmpty(message = "Candidate list must not be empty")
    private List<CandidateInviteItem> candidateList;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidateInviteItem {
        private Long candidateId;
        @NotBlank(message = "Candidate email is required")
        private String email;
        @NotBlank(message = "Candidate name is required")
        private String fullName;
    }
}
