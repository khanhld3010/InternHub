package org.example.internservice.intern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.intern.dto.InternEvaluationRequest;
import org.example.internservice.intern.dto.InternEvaluationResponse;
import org.example.internservice.intern.service.InternEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interns/{internCode}/evaluations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Intern Milestone & Final Evaluation", description = "APIs đánh giá mốc và tổng kết kỹ năng, thái độ thực tập sinh")
public class InternEvaluationController {

    private final InternEvaluationService evaluationService;

    @PostMapping
    @Operation(summary = "Lưu hoặc Cập nhật đánh giá tổng kết / mốc kỳ (Draft hoặc Submit)")
    public ResponseEntity<ApiResponse<InternEvaluationResponse>> saveOrUpdateEvaluation(
            @PathVariable String internCode,
            @Valid @RequestBody InternEvaluationRequest request
    ) {
        InternEvaluationResponse response = evaluationService.saveOrUpdateEvaluation(internCode, request);
        return ResponseEntity.ok(ApiResponse.<InternEvaluationResponse>builder()
                .code(200)
                .message("Lưu đánh giá tổng kết thành công")
                .data(response)
                .build());
    }

    @GetMapping
    @Operation(summary = "Lấy thông tin đánh giá theo loại (MIDTERM, FINAL) hoặc tất cả")
    public ResponseEntity<ApiResponse<InternEvaluationResponse>> getEvaluationByType(
            @PathVariable String internCode,
            @RequestParam(required = false, defaultValue = "FINAL") String type
    ) {
        InternEvaluationResponse response = evaluationService.getEvaluationByType(internCode, type);
        return ResponseEntity.ok(ApiResponse.<InternEvaluationResponse>builder()
                .code(200)
                .message(response != null ? "Lấy thông tin đánh giá thành công" : "Chưa có đánh giá cho loại này")
                .data(response)
                .build());
    }

    @GetMapping("/all")
    @Operation(summary = "Lấy toàn bộ lịch sử các mốc đánh giá của TTS")
    public ResponseEntity<ApiResponse<List<InternEvaluationResponse>>> getAllEvaluations(
            @PathVariable String internCode
    ) {
        List<InternEvaluationResponse> responses = evaluationService.getAllEvaluationsByIntern(internCode);
        return ResponseEntity.ok(ApiResponse.<List<InternEvaluationResponse>>builder()
                .code(200)
                .message("Lấy danh sách đánh giá thành công")
                .data(responses)
                .build());
    }

    @PatchMapping("/hr-approve")
    @Operation(summary = "HR phê duyệt đánh giá cuối kỳ và hoàn thành thực tập cho sinh viên")
    public ResponseEntity<ApiResponse<InternEvaluationResponse>> hrApproveEvaluation(
            @PathVariable String internCode,
            @Valid @RequestBody org.example.internservice.intern.dto.HrApproveEvaluationRequest request,
            java.security.Principal principal
    ) {
        String hrUsername = principal != null ? principal.getName() : "HR Specialist";
        InternEvaluationResponse response = evaluationService.hrApproveEvaluation(internCode, request, hrUsername);
        return ResponseEntity.ok(ApiResponse.<InternEvaluationResponse>builder()
                .code(200)
                .message("Phê duyệt đánh giá cuối kỳ thành công")
                .data(response)
                .build());
    }
}
