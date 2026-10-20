package org.example.internservice.quiz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.quiz.dto.request.SubmitQuizRequest;
import org.example.internservice.quiz.dto.response.QuizAssignmentResponse;
import org.example.internservice.quiz.dto.response.QuizResultResponse;
import org.example.internservice.quiz.dto.response.QuizTakingResponse;
import org.example.internservice.quiz.service.QuizInternService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/interns/me/quizzes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERN')")
public class InternQuizController {

    private final QuizInternService quizInternService;

    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<List<QuizAssignmentResponse>>> getMyAssignedQuizzes(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<QuizAssignmentResponse> response = quizInternService.getMyAssignedQuizzes(userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách bài Quiz được giao thành công", response));
    }

    @PostMapping("/assignments/{assignmentId}/start")
    public ResponseEntity<ApiResponse<QuizTakingResponse>> startQuizAttempt(
            @PathVariable("assignmentId") Long assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QuizTakingResponse response = quizInternService.startQuizAttempt(assignmentId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Bắt đầu làm bài thi Quiz thành công", response));
    }

    @PostMapping("/submissions/{submissionId}/submit")
    public ResponseEntity<ApiResponse<QuizResultResponse>> submitQuizAttempt(
            @PathVariable("submissionId") Long submissionId,
            @Valid @RequestBody SubmitQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QuizResultResponse response = quizInternService.submitQuizAttempt(submissionId, request, userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Nộp bài thi thành công. Đã chấm điểm tự động và mở khóa đáp án chi tiết.", response));
    }

    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<ApiResponse<QuizResultResponse>> getSubmissionResult(
            @PathVariable("submissionId") Long submissionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QuizResultResponse response = quizInternService.getSubmissionResult(submissionId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy kết quả bài thi thành công", response));
    }
}
