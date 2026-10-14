package org.example.aiservice.modules.quiz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.core.provider.AiProvider;
import org.example.aiservice.modules.quiz.dto.request.AiQuizGenerateRequest;
import org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse;
import org.example.aiservice.modules.quiz.service.AiQuizService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuizServiceImpl implements AiQuizService {

    private final AiProvider aiProvider;

    @Override
    public AiQuizGenerateResponse generateQuiz(AiQuizGenerateRequest request) {
        log.info("Bắt đầu xử lý sinh bài Quiz qua AI. Topic: '{}', Program: '{}', Group: '{}', Count: {}, Difficulty: {}",
                request.getTopic(), request.getProgramName(), request.getGroupName(),
                request.getQuestionCount(), request.getDifficulty());

        int count = (request.getQuestionCount() != null && request.getQuestionCount() >= 3)
                ? request.getQuestionCount() : 10;

        return aiProvider.generateQuizQuestions(
                request.getContextText(),
                request.getTopic(),
                request.getProgramName(),
                request.getGroupName(),
                count,
                request.getDifficulty()
        );
    }
}
