package org.example.internservice.intern.service;

import org.example.internservice.intern.dto.request.RequestReportRevisionRequest;
import org.example.internservice.intern.dto.request.WeeklyAssessmentRequest;
import org.example.internservice.intern.dto.response.MentorTriageOverviewResponse;
import org.example.internservice.intern.dto.response.MentorWeeklyReportReviewResponse;
import org.example.internservice.intern.dto.response.ReportRevisionResponse;
import org.example.internservice.intern.dto.response.WeeklyAssessmentResponse;

import java.util.List;

public interface WeeklyAssessmentService {

    WeeklyAssessmentResponse saveAssessment(String internCode, WeeklyAssessmentRequest request, Long mentorId, String mentorName);

    WeeklyAssessmentResponse saveAssessment(String internCode, WeeklyAssessmentRequest request, Long mentorId, String mentorName, String role);

    List<WeeklyAssessmentResponse> getAssessmentHistory(String internCode, String userRole);

    MentorTriageOverviewResponse getMentorTriageOverview(Long mentorId);

    MentorWeeklyReportReviewResponse getWeeklyReportForMentor(String internCode, Integer weekNumber, Long mentorId, String role);

    ReportRevisionResponse requestReportRevision(String internCode, Integer weekNumber, RequestReportRevisionRequest request, Long mentorId, String mentorName, String role);
}
