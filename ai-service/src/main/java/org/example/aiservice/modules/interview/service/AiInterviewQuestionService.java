package org.example.aiservice.modules.interview.service;

import org.example.aiservice.modules.interview.dto.request.GenerateInterviewQuestionsRequest;
import org.example.aiservice.modules.interview.dto.request.UpdateInterviewQuestionSetRequest;
import org.example.aiservice.modules.interview.dto.response.InterviewQuestionSetResponse;

import org.example.aiservice.modules.interview.dto.response.QuestionGenerationJobResponse;

import java.util.List;

public interface AiInterviewQuestionService {

    InterviewQuestionSetResponse generateQuestions(
            GenerateInterviewQuestionsRequest request,
            String userId,
            String userName,
            String authHeader
    );

    QuestionGenerationJobResponse startAsyncQuestionGeneration(
            GenerateInterviewQuestionsRequest request,
            String userId,
            String userName,
            String authHeader
    );

    void executeAsyncQuestionGeneration(
            String jobId,
            GenerateInterviewQuestionsRequest request,
            String userId,
            String userName,
            String authHeader
    );

    QuestionGenerationJobResponse getGenerationJobStatus(String jobId);

    InterviewQuestionSetResponse getQuestionSetById(Long id);

    List<InterviewQuestionSetResponse> getQuestionSetsByProgram(Long programId);

    List<InterviewQuestionSetResponse> getQuestionSetsByIntern(Long internId);

    InterviewQuestionSetResponse updateQuestionSet(Long id, UpdateInterviewQuestionSetRequest request);

    void deleteQuestionSet(Long id);

    InterviewQuestionSetResponse.InterviewQuestionResponse regenerateSingleQuestion(Long setId, Long questionId);

    byte[] exportInterviewEvaluationSheetPdf(Long setId);
}

