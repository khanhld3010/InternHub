package org.example.internservice.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizGenerationJobResponse {

    private String jobId;
    private Long programId;
    private String topic;
    private String difficulty;
    private Integer questionCount;
    private String status; // PROCESSING, COMPLETED, FAILED, NOT_FOUND
    private int percent;
    private String currentStage;
    private Long quizId;
    private QuizDetailResponse result;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
