package org.example.aiservice.modules.report.controller;

import lombok.RequiredArgsConstructor;
import org.example.aiservice.modules.report.dto.AiEvaluateWeeklyReportRequest;
import org.example.aiservice.modules.report.dto.AiWeeklyReportEvaluationResult;
import org.example.aiservice.modules.report.service.AiWeeklyReportGraderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/reports")
@RequiredArgsConstructor
public class AiWeeklyReportGraderController {

    private final AiWeeklyReportGraderService graderService;
    private final org.example.aiservice.service.AiRateLimiterService rateLimiterService;

    @PostMapping("/evaluate-weekly")
    public ResponseEntity<AiWeeklyReportEvaluationResult> evaluateWeeklyReport(
            @RequestBody AiEvaluateWeeklyReportRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            jakarta.servlet.http.HttpServletRequest httpServletRequest
    ) {
        // Ưu tiên userId từ header / token, fallback sang internCode hoặc IP người gọi
        String callerId = userIdHeader;
        if (callerId == null || callerId.isBlank()) {
            callerId = request.getInternCode();
        }
        if (callerId == null || callerId.isBlank()) {
            callerId = httpServletRequest.getRemoteAddr();
        }

        // AI-12: Kiểm tra Rate Limiting & Quota cho REPORT_ASSISTANT (Fail-closed)
        rateLimiterService.checkFeatureRateLimit("REPORT_ASSISTANT", callerId);

        AiWeeklyReportEvaluationResult result = graderService.evaluateReport(request);
        return ResponseEntity.ok(result);
    }
}
