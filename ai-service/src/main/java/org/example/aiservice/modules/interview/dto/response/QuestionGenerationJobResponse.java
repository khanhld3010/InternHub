package org.example.aiservice.modules.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionGenerationJobResponse {

    private String jobId;
    private Long programId;
    private Long internId;
    private String targetRole;
    private String status; // PROCESSING, COMPLETED, FAILED
    private int percent;
    private String currentStage;
    private Long questionSetId;
    private InterviewQuestionSetResponse result;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
