package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.response.WeeklyReportAiEvaluationResponse;

public interface InternWeeklyReportAiEvaluationService {

    WeeklyReportAiEvaluationResponse getActiveEvaluation(Long reportId, String currentUser);

    WeeklyReportAiEvaluationResponse triggerEvaluation(Long reportId, String currentUser);
}
