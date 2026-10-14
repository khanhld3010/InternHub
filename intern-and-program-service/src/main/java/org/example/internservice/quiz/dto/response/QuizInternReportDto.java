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
public class QuizInternReportDto {

    private Long internId;
    private String internCode;
    private String fullName;
    private String email;
    private String groupName;
    private Integer attemptsCount;
    private Integer highestScore;
    private Integer totalPoints;
    private Boolean passed;
    private LocalDateTime latestSubmittedAt;
}
