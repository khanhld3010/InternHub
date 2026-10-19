package org.example.aiservice.modules.quiz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.dto.response.ApiResponse;
import org.example.aiservice.modules.quiz.dto.request.AiQuizGenerateRequest;
import org.example.aiservice.modules.quiz.dto.response.AiQuizGenerateResponse;
import org.example.aiservice.modules.quiz.service.AiQuizService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/ai/quiz")
@RequiredArgsConstructor
public class AiQuizController {

    private final AiQuizService aiQuizService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiQuizGenerateResponse>> generateQuiz(
            @Valid @RequestBody AiQuizGenerateRequest request
    ) {
        log.info("Nhận yêu cầu sinh câu hỏi Quiz bằng AI cho chủ đề: {}", request.getTopic());
        AiQuizGenerateResponse response = aiQuizService.generateQuiz(request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<AiQuizGenerateResponse>builder()
                        .code(HttpStatus.OK.value())
                        .message("Sinh bộ câu hỏi trắc nghiệm bằng AI thành công")
                        .data(response)
                        .build());
    }
}
