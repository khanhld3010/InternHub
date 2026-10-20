package org.example.internservice.quiz.service;

import org.example.internservice.quiz.dto.request.AssignQuizRequest;
import org.example.internservice.quiz.dto.request.CreateQuizRequest;
import org.example.internservice.quiz.dto.request.GenerateAiQuizRequest;
import org.example.internservice.quiz.dto.request.UpdateQuizRequest;
import org.example.internservice.quiz.dto.response.QuizDetailResponse;
import org.example.internservice.quiz.dto.response.QuizReportResponse;
import org.example.internservice.quiz.dto.response.QuizSummaryResponse;
import org.example.internservice.security.CustomUserDetails;

import java.util.List;

public interface QuizMentorService {

    QuizDetailResponse generateAiQuiz(GenerateAiQuizRequest request, CustomUserDetails userDetails);

    QuizDetailResponse createQuiz(CreateQuizRequest request, CustomUserDetails userDetails);

    QuizDetailResponse updateQuiz(Long quizId, UpdateQuizRequest request, CustomUserDetails userDetails);

    QuizDetailResponse getQuizDetail(Long quizId, CustomUserDetails userDetails);

    List<QuizSummaryResponse> getQuizzesByProgram(Long programId, CustomUserDetails userDetails);

    void assignQuiz(Long quizId, AssignQuizRequest request, CustomUserDetails userDetails);

    QuizReportResponse getQuizReport(Long quizId, CustomUserDetails userDetails);
}
