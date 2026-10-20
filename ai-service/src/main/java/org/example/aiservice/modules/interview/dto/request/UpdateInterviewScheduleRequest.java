package org.example.aiservice.modules.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.example.aiservice.modules.interview.enums.InterviewMode;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateInterviewScheduleRequest {

    @NotBlank(message = "Title must not be blank")
    private String title;

    private Long questionSetId;

    private InterviewMode interviewMode;

    private Integer durationPerQuestionSec;

    private Integer totalDurationMinutes;

    private Boolean allowRevisitQuestions;

    private Integer maxBlurStrikes;

    private Boolean autoSubmitOnExceed;

    private Integer expireHours;

    private List<CreateInterviewScheduleRequest.CandidateInviteItem> candidateList;
}
