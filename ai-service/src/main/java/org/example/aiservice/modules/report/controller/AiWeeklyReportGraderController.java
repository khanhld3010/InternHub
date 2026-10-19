package org.example.aiservice.modules.report.controller;

import lombok.RequiredArgsConstructor;
import org.example.aiservice.modules.report.dto.AiEvaluateWeeklyReportRequest;
import org.example.aiservice.modules.report.dto.AiWeeklyReportEvaluationResult;
import org.example.aiservice.modules.report.service.AiWeeklyReportGraderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/reports")
@RequiredArgsConstructor
public class AiWeeklyReportGraderController {

    private final AiWeeklyReportGraderService graderService;

    @PostMapping("/evaluate-weekly")
    public ResponseEntity<AiWeeklyReportEvaluationResult> evaluateWeeklyReport(
            @RequestBody AiEvaluateWeeklyReportRequest request
    ) {
        AiWeeklyReportEvaluationResult result = graderService.evaluateReport(request);
        return ResponseEntity.ok(result);
    }
}
