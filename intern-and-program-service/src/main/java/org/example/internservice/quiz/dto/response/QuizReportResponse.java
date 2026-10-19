package org.example.internservice.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizReportResponse {

    private Long quizId;
    private String quizTitle;
    private Integer totalAssigned;
    private Integer completedCount;
    private Integer passedCount;
    private Double averageScore;

    @Builder.Default
    private List<QuizInternReportDto> internReports = new ArrayList<>();
}
