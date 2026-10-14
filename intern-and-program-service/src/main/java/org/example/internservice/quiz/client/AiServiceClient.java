package org.example.internservice.quiz.client;

import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.quiz.client.dto.AiQuizGenerateClientRequest;
import org.example.internservice.quiz.client.dto.AiQuizGenerateClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "service-ai", url = "${services.ai-service.url:http://localhost:8086}")
public interface AiServiceClient {

    @PostMapping("/api/ai/quiz/generate")
    ApiResponse<AiQuizGenerateClientResponse> generateQuiz(
            @RequestBody AiQuizGenerateClientRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    );
}
