package org.example.aiservice.modules.screening.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.dto.response.ApiResponse;
import org.example.aiservice.modules.screening.dto.request.BatchScreeningRequest;
import org.example.aiservice.modules.screening.dto.response.BatchJobCreatedResponse;
import org.example.aiservice.modules.screening.dto.response.BatchJobStatusResponse;
import org.example.aiservice.modules.screening.dto.response.ScreeningResultDto;
import org.example.aiservice.modules.screening.service.AiScreeningService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ai/screening")
@RequiredArgsConstructor
public class AiScreeningController {

    private final AiScreeningService aiScreeningService;
    private final org.example.aiservice.service.AiRateLimiterService rateLimiterService;

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<BatchJobCreatedResponse>> startBatchScreening(
            @Valid @RequestBody BatchScreeningRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            jakarta.servlet.http.HttpServletRequest httpServletRequest
    ) {
        String callerId = userIdHeader;
        if (callerId == null || callerId.isBlank()) {
            callerId = httpServletRequest.getRemoteAddr();
        }

        log.info("Nhận yêu cầu phân tích AI hàng loạt: programId={}, số lượng={}, requester={}",
                request.getProgramId(), request.getInternIds().size(), callerId);

        // AI-12: Kiểm tra Rate Limiting & Quota cho CV_SCREENING (Fail-closed)
        rateLimiterService.checkFeatureRateLimit("CV_SCREENING", callerId);

        BatchJobCreatedResponse response = aiScreeningService.startBatchScreening(request, authHeader);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(202, "Đã tiếp nhận yêu cầu phân tích hàng loạt bằng AI", response));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<BatchJobStatusResponse>> getBatchJobStatus(
            @PathVariable("jobId") String jobId
    ) {
        BatchJobStatusResponse status = aiScreeningService.getBatchJobStatus(jobId);
        return ResponseEntity.ok(ApiResponse.success(200, "Trạng thái công việc phân tích AI", status));
    }

    @GetMapping("/interns/{internId}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ScreeningResultDto>> getInternEvaluation(
            @PathVariable("internId") Long internId
    ) {
        ScreeningResultDto result = aiScreeningService.getInternEvaluation(internId);
        if (result == null) {
            return ResponseEntity.ok(ApiResponse.success(200, "Chưa có kết quả đánh giá AI cho ứng viên này", null));
        }
        return ResponseEntity.ok(ApiResponse.success(200, "Chi tiết đánh giá AI", result));
    }
}
