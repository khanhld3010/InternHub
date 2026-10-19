package org.example.aiservice.modules.quiz.service;

import org.example.aiservice.modules.quiz.dto.request.AiQuizGenerateRequest;
import org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse;

public interface AiQuizService {

    AiQuizGenerateResponse generateQuiz(AiQuizGenerateRequest request);
}
