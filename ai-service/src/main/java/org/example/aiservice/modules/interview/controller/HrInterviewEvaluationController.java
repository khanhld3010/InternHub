package org.example.aiservice.modules.interview.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.aiservice.modules.interview.dto.request.HrEvaluationReviewRequest;
import org.example.aiservice.modules.interview.dto.request.RegradeRequest;
import org.example.aiservice.modules.interview.dto.response.EvaluationDossierResponse;
import org.example.aiservice.modules.interview.service.HrInterviewEvaluationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/interviews/schedules/sessions/{sessionId}/evaluation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
public class HrInterviewEvaluationController {

    private final HrInterviewEvaluationService evaluationService;
    private final org.example.aiservice.modules.interview.service.EvaluationPdfExportService pdfExportService;

    /**
     * 7.1 Lấy Phiếu Đánh Giá Chi Tiết Phiên Phỏng Vấn (Evaluation Dossier)
     */
    @GetMapping
    public ResponseEntity<EvaluationDossierResponse> getEvaluationDossier(@PathVariable Long sessionId) {
        EvaluationDossierResponse response = evaluationService.getEvaluationDossier(sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * 7.5 Xuất Báo Cáo Kết Quả Phỏng Vấn Dạng PDF (Evaluation Dossier PDF Export)
     */
    @GetMapping("/export-pdf")
    public ResponseEntity<byte[]> exportEvaluationPdf(@PathVariable Long sessionId) {
        byte[] pdfBytes = pdfExportService.generateEvaluationPdf(sessionId);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Interview_Evaluation_Session_" + sessionId + ".pdf\"")
                .body(pdfBytes);
    }

    /**
     * 7.2 HR Phê Duyệt hoặc Điều Chỉnh Kết Quả Đánh Giá (Lưu Nháp / Chốt Sổ)
     */
    @PostMapping("/review")
    public ResponseEntity<EvaluationDossierResponse> reviewEvaluation(
            @PathVariable Long sessionId,
            @Valid @RequestBody HrEvaluationReviewRequest request,
            Authentication authentication
    ) {
        String hrUserId = authentication != null ? authentication.getName() : "HR_USER";
        EvaluationDossierResponse response = evaluationService.reviewEvaluation(sessionId, request, hrUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 7.3 Hủy Phê Duyệt Để Chấm Lại Hoặc Sửa Lại Điểm (Revoke Approval)
     */
    @PostMapping("/revoke-approval")
    public ResponseEntity<EvaluationDossierResponse> revokeApproval(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        String hrUserId = authentication != null ? authentication.getName() : "HR_USER";
        EvaluationDossierResponse response = evaluationService.revokeApproval(sessionId, hrUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 7.4 Kích Hoạt Chấm Lại Bằng AI (Regrade Trigger with Versioning)
     */
    @PostMapping("/regrade")
    public ResponseEntity<Map<String, Object>> triggerRegrade(
            @PathVariable Long sessionId,
            @Valid @RequestBody RegradeRequest request,
            Authentication authentication
    ) {
        String hrUserId = authentication != null ? authentication.getName() : "HR_USER";
        Map<String, Object> response = evaluationService.triggerRegrade(sessionId, request, hrUserId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
