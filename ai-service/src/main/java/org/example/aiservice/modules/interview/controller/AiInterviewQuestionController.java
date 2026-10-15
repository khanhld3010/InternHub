package org.example.aiservice.modules.interview.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.modules.interview.dto.request.GenerateInterviewQuestionsRequest;
import org.example.aiservice.modules.interview.dto.request.UpdateInterviewQuestionSetRequest;
import org.example.aiservice.modules.interview.dto.response.InterviewQuestionSetResponse;
import org.example.aiservice.modules.interview.service.AiInterviewQuestionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/interview-questions")
@RequiredArgsConstructor
@Slf4j
public class AiInterviewQuestionController {

    private final AiInterviewQuestionService aiInterviewQuestionService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('HR', 'MENTOR', 'ADMIN') or hasAuthority('AI_INTERVIEW_MANAGE')")
    public ResponseEntity<InterviewQuestionSetResponse> generateQuestions(
            @Valid @RequestBody GenerateInterviewQuestionsRequest request,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "system") String userId,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "HR/Mentor") String userName,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        log.info("REST request to generate interview questions: programId={}, internId={}, role={}",
                request.getProgramId(), request.getInternId(), request.getTargetRole());
        InterviewQuestionSetResponse response = aiInterviewQuestionService.generateQuestions(
                request, userId, userName, authHeader);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sets/{id}")
    @PreAuthorize("hasAnyRole('HR', 'MENTOR', 'ADMIN') or hasAuthority('AI_INTERVIEW_MANAGE')")
    public ResponseEntity<InterviewQuestionSetResponse> getQuestionSetById(@PathVariable Long id) {
        return ResponseEntity.ok(aiInterviewQuestionService.getQuestionSetById(id));
    }

    @GetMapping("/sets")
    @PreAuthorize("hasAnyRole('HR', 'MENTOR', 'ADMIN') or hasAuthority('AI_INTERVIEW_MANAGE')")
    public ResponseEntity<List<InterviewQuestionSetResponse>> getQuestionSets(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long internId
    ) {
        if (internId != null) {
            return ResponseEntity.ok(aiInterviewQuestionService.getQuestionSetsByIntern(internId));
        }
        if (programId != null) {
            return ResponseEntity.ok(aiInterviewQuestionService.getQuestionSetsByProgram(programId));
        }
        return ResponseEntity.badRequest().build();
    }

    @PutMapping("/sets/{id}")
    @PreAuthorize("hasAnyRole('HR', 'MENTOR', 'ADMIN') or hasAuthority('AI_INTERVIEW_MANAGE')")
    public ResponseEntity<InterviewQuestionSetResponse> updateQuestionSet(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInterviewQuestionSetRequest request
    ) {
        return ResponseEntity.ok(aiInterviewQuestionService.updateQuestionSet(id, request));
    }

    @DeleteMapping("/sets/{id}")
    @PreAuthorize("hasAnyRole('HR', 'MENTOR', 'ADMIN') or hasAuthority('AI_INTERVIEW_MANAGE')")
    public ResponseEntity<Void> deleteQuestionSet(@PathVariable Long id) {
        aiInterviewQuestionService.deleteQuestionSet(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sets/{setId}/questions/{questionId}/regenerate")
    @PreAuthorize("hasAnyRole('HR', 'MENTOR', 'ADMIN') or hasAuthority('AI_INTERVIEW_MANAGE')")
    public ResponseEntity<InterviewQuestionSetResponse.InterviewQuestionResponse> regenerateSingleQuestion(
            @PathVariable Long setId,
            @PathVariable Long questionId
    ) {
        return ResponseEntity.ok(aiInterviewQuestionService.regenerateSingleQuestion(setId, questionId));
    }

    @GetMapping("/sets/{id}/export-pdf")
    @PreAuthorize("hasAnyRole('HR', 'MENTOR', 'ADMIN') or hasAuthority('AI_INTERVIEW_MANAGE')")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        byte[] pdfBytes = aiInterviewQuestionService.exportInterviewEvaluationSheetPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"interview-evaluation-set-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
