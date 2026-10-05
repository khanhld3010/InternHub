package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.request.WeeklyAssessmentRequest;
import org.example.internservice.intern.dto.response.MentorTriageOverviewResponse;
import org.example.internservice.intern.dto.response.WeeklyAssessmentResponse;

import java.util.List;

public interface WeeklyAssessmentService {

    WeeklyAssessmentResponse saveAssessment(String internCode, WeeklyAssessmentRequest request, Long mentorId, String mentorName);

    List<WeeklyAssessmentResponse> getAssessmentHistory(String internCode, String userRole);

    MentorTriageOverviewResponse getMentorTriageOverview(Long mentorId);
}
