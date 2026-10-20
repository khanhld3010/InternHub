package org.example.internservice.quiz.service;

import org.example.internservice.quiz.dto.request.SubmitQuizRequest;
import org.example.internservice.quiz.dto.response.QuizAssignmentResponse;
import org.example.internservice.quiz.dto.response.QuizResultResponse;
import org.example.internservice.quiz.dto.response.QuizTakingResponse;
import org.example.internservice.security.CustomUserDetails;

import java.util.List;

public interface QuizInternService {

    List<QuizAssignmentResponse> getMyAssignedQuizzes(CustomUserDetails userDetails);

    QuizTakingResponse startQuizAttempt(Long assignmentId, CustomUserDetails userDetails);

    QuizResultResponse submitQuizAttempt(Long submissionId, SubmitQuizRequest request, CustomUserDetails userDetails);

    QuizResultResponse getSubmissionResult(Long submissionId, CustomUserDetails userDetails);
}
