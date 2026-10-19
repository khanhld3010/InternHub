package org.example.aiservice.modules.interview.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.aiservice.modules.interview.dto.request.SaveCandidateAnswerRequest;
import org.example.aiservice.modules.interview.dto.response.CandidateQuestionResponse;
import org.example.aiservice.modules.interview.dto.response.CandidateRecoveryStateResponse;
import org.example.aiservice.modules.interview.dto.response.SaveCandidateAnswerResponse;
import org.example.aiservice.modules.interview.service.CandidateSessionExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/interviews/candidate/sessions")
@RequiredArgsConstructor
public class CandidateInterviewExecutionController {

    private final CandidateSessionExecutionService executionService;
    private final org.example.aiservice.modules.interview.service.InterviewAuditAndSubmissionService auditAndSubmissionService;
    private final org.example.aiservice.service.AiRateLimiterService rateLimiterService;

    @PostMapping("/{sessionId}/start")
    public ResponseEntity<CandidateQuestionResponse> startSession(
            @PathVariable Long sessionId,
            Authentication authentication) {
        String authenticatedUserId = authentication != null ? authentication.getName() : null;
        CandidateQuestionResponse response = executionService.startSession(sessionId, authenticatedUserId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{sessionId}/answers/{questionId}")
    public ResponseEntity<SaveCandidateAnswerResponse> saveAnswer(
            @PathVariable Long sessionId,
            @PathVariable Long questionId,
            @Valid @RequestBody SaveCandidateAnswerRequest request,
            Authentication authentication) {
        String authenticatedUserId = authentication != null ? authentication.getName() : null;
        SaveCandidateAnswerResponse response = executionService.saveAnswer(sessionId, questionId, request, authenticatedUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}/recovery")
    public ResponseEntity<CandidateRecoveryStateResponse> getRecoveryState(
            @PathVariable Long sessionId,
            Authentication authentication) {
        String authenticatedUserId = authentication != null ? authentication.getName() : null;
        CandidateRecoveryStateResponse response = executionService.getRecoveryState(sessionId, authenticatedUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/evidence")
    public ResponseEntity<org.example.aiservice.modules.interview.dto.response.CandidateEvidenceResponse> recordEvidence(
            @PathVariable Long sessionId,
            @Valid @RequestBody org.example.aiservice.modules.interview.dto.request.CandidateEvidenceRequest request,
            Authentication authentication) {
        rateLimiterService.checkEvidenceRateLimit(sessionId);
        String authenticatedUserId = authentication != null ? authentication.getName() : null;
        var response = auditAndSubmissionService.recordEvidence(sessionId, request, authenticatedUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<org.example.aiservice.modules.interview.dto.response.SubmitInterviewSessionResponse> submitSession(
            @PathVariable Long sessionId,
            @RequestParam(required = false) org.example.aiservice.modules.interview.enums.SubmissionReason reason,
            Authentication authentication) {
        String authenticatedUserId = authentication != null ? authentication.getName() : null;
        var response = auditAndSubmissionService.submitSession(sessionId, reason, authenticatedUserId);
        return ResponseEntity.ok(response);
    }
}
