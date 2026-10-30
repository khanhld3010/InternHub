package org.example.internservice.intern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.intern.dto.response.WeeklyReportAiEvaluationResponse;
import org.example.internservice.intern.service.InternWeeklyReportAiEvaluationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mentor/weekly-reports")
@RequiredArgsConstructor
@Tag(name = "Mentor Weekly Report AI Evaluation", description = "Phân tích, tóm tắt và đối soát báo cáo tuần bằng AI dành cho Mentor & HR")
public class MentorWeeklyReportAiEvaluationController {

    private final InternWeeklyReportAiEvaluationService aiEvaluationService;

    @Operation(summary = "Lấy kết quả phân tích AI hiện hành của báo cáo tuần")
    @GetMapping("/{reportId}/ai-evaluation")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN') or hasAuthority('AI_REPORT_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<WeeklyReportAiEvaluationResponse>> getActiveEvaluation(
            @PathVariable Long reportId,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "MENTOR";
        WeeklyReportAiEvaluationResponse response = aiEvaluationService.getActiveEvaluation(reportId, username);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Lấy kết quả phân tích AI thành công", response));
    }

    @Operation(summary = "Kích hoạt phân tích hoặc tái phân tích báo cáo tuần bằng AI")
    @PostMapping("/{reportId}/ai-evaluate")
    @PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN') or hasAuthority('AI_REPORT_ASSISTANT_USE')")
    public ResponseEntity<ApiResponse<WeeklyReportAiEvaluationResponse>> triggerEvaluation(
            @PathVariable Long reportId,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "MENTOR";
        WeeklyReportAiEvaluationResponse response = aiEvaluationService.triggerEvaluation(reportId, username);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Phân tích báo cáo tuần bằng AI thành công", response));
    }
}
