package org.example.internservice.quiz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.quiz.dto.request.AssignQuizRequest;
import org.example.internservice.quiz.dto.request.CreateQuizRequest;
import org.example.internservice.quiz.dto.request.GenerateAiQuizRequest;
import org.example.internservice.quiz.dto.request.UpdateQuizRequest;
import org.example.internservice.quiz.dto.response.QuizDetailResponse;
import org.example.internservice.quiz.dto.response.QuizReportResponse;
import org.example.internservice.quiz.dto.response.QuizSummaryResponse;
import org.example.internservice.quiz.service.QuizMentorService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")
public class MentorQuizController {

    private final QuizMentorService quizMentorService;

    @PostMapping("/generate-ai")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> generateAiQuiz(
            @Valid @RequestBody GenerateAiQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("REST yêu cầu AI sinh bài Quiz cho chương trình ID: {}", request.getProgramId());
        QuizDetailResponse response = quizMentorService.generateAiQuiz(request, userDetails);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(),
                        "AI đã sinh thành công bộ câu hỏi bài Quiz nháp để Mentor kiểm duyệt", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuizDetailResponse>> createQuiz(
            @Valid @RequestBody CreateQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QuizDetailResponse response = quizMentorService.createQuiz(request, userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo bài Quiz mới thành công", response));
    }

    @PutMapping("/{quizId}")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> updateQuiz(
            @PathVariable("quizId") Long quizId,
            @Valid @RequestBody UpdateQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QuizDetailResponse response = quizMentorService.updateQuiz(quizId, request, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Cập nhật nội dung bài Quiz thành công", response));
    }

    @GetMapping("/{quizId}")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> getQuizDetail(
            @PathVariable("quizId") Long quizId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QuizDetailResponse response = quizMentorService.getQuizDetail(quizId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy chi tiết bài Quiz thành công", response));
    }

    @GetMapping({"/programs/{programId}", "/program/{programId}"})
    public ResponseEntity<ApiResponse<List<QuizSummaryResponse>>> getQuizzesByProgram(
            @PathVariable("programId") Long programId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<QuizSummaryResponse> response = quizMentorService.getQuizzesByProgram(programId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy danh sách bài Quiz trong chương trình thành công", response));
    }

    @PostMapping("/{quizId}/assign")
    public ResponseEntity<ApiResponse<Void>> assignQuiz(
            @PathVariable("quizId") Long quizId,
            @Valid @RequestBody AssignQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        quizMentorService.assignQuiz(quizId, request, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Giao bài Quiz và đồng bộ thẻ nhiệm vụ Kanban thành công", null));
    }

    @GetMapping("/{quizId}/report")
    public ResponseEntity<ApiResponse<QuizReportResponse>> getQuizReport(
            @PathVariable("quizId") Long quizId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QuizReportResponse response = quizMentorService.getQuizReport(quizId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy báo cáo kết quả bài Quiz thành công", response));
    }
}
