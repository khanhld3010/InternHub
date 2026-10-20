package org.example.aiservice.modules.report.controller;

import lombok.RequiredArgsConstructor;
import org.example.aiservice.modules.report.dto.AiEvaluateWeeklyReportRequest;
import org.example.aiservice.modules.report.dto.AiWeeklyReportEvaluationResult;
import org.example.aiservice.modules.report.service.AiWeeklyReportGraderService;
import org.example.aiservice.security.PromptInjectionGuardService;
import org.example.aiservice.service.AiRateLimiterService;
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
    private final AiRateLimiterService rateLimiterService;
    private final PromptInjectionGuardService promptGuardService;

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

        // AI-15: Chặn Prompt Injection / Jailbreak fail-fast các trường văn bản tự do của báo cáo
        if (request != null) {
            promptGuardService.validatePromptInput(request.getCompletedTasksSummary(), "WEEKLY_REPORT", callerId);
            promptGuardService.validatePromptInput(request.getUnfinishedTasksSummary(), "WEEKLY_REPORT", callerId);
            promptGuardService.validatePromptInput(request.getDifficultiesAndChallenges(), "WEEKLY_REPORT", callerId);
            promptGuardService.validatePromptInput(request.getLearningsAndKnowledge(), "WEEKLY_REPORT", callerId);
        }

        AiWeeklyReportEvaluationResult result = graderService.evaluateReport(request);
        return ResponseEntity.ok(result);
    }
}
