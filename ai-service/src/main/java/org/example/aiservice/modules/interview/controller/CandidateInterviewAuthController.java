package org.example.aiservice.modules.interview.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.aiservice.modules.interview.dto.request.ExchangeTokenRequest;
import org.example.aiservice.modules.interview.dto.response.CandidateSessionAccessResponse;
import org.example.aiservice.modules.interview.service.CandidateAuthService;
import org.example.aiservice.modules.interview.dto.response.InternInterviewItemResponse;
import org.example.aiservice.modules.interview.service.InterviewScheduleManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/interviews/candidate")
@RequiredArgsConstructor
public class CandidateInterviewAuthController {

    private final CandidateAuthService candidateAuthService;
    private final InterviewScheduleManagementService scheduleManagementService;
    private final org.example.aiservice.service.AiRateLimiterService rateLimiterService;

    @PostMapping("/exchange-token")
    public ResponseEntity<CandidateSessionAccessResponse> exchangeToken(
            @Valid @RequestBody ExchangeTokenRequest request,
            HttpServletRequest httpServletRequest) {

        String clientIp = httpServletRequest.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = httpServletRequest.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }

        rateLimiterService.checkExchangeTokenRateLimit(clientIp);

        CandidateSessionAccessResponse response = candidateAuthService.exchangeToken(request, clientIp);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-interviews")
    public ResponseEntity<List<InternInterviewItemResponse>> getMyInterviews(
            Authentication authentication,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String usernameOrEmail = (authentication != null) ? authentication.getName() : "";
        List<InternInterviewItemResponse> response = scheduleManagementService.getMyInterviews(usernameOrEmail, authHeader);
        return ResponseEntity.ok(response);
    }
}
