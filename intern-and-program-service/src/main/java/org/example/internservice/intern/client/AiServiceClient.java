package org.example.internservice.intern.client;

import org.example.internservice.intern.client.dto.AiEvaluateWeeklyReportRequest;
import org.example.internservice.intern.client.dto.AiWeeklyReportEvaluationResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "ai-service", url = "${app.ai-service.url:http://ai-service:8086}")
public interface AiServiceClient {

    @PostMapping("/api/v1/ai/reports/evaluate-weekly")
    AiWeeklyReportEvaluationResult evaluateWeeklyReport(
            @RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody AiEvaluateWeeklyReportRequest request
    );
}
