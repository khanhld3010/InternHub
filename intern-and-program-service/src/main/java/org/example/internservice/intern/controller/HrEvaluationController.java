package org.example.internservice.intern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.intern.dto.HrEvaluationSummaryItem;
import org.example.internservice.intern.service.InternEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations/hr")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "HR Evaluation Management", description = "APIs dành cho HR quản lý, rà soát và xuất báo cáo đánh giá cuối kỳ")
public class HrEvaluationController {

    private final InternEvaluationService evaluationService;

    @GetMapping("/summary")
    @Operation(summary = "Lấy danh sách tổng hợp đánh giá thực tập sinh cho HR")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<HrEvaluationSummaryItem>>> getHrEvaluationSummary(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) String university,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) String keyword
    ) {
        log.info("HR query evaluation summary: programId={}, university={}, status={}, keyword={}",
                programId, university, status, keyword);
        List<HrEvaluationSummaryItem> result = evaluationService.getHrEvaluationSummary(programId, university, status, keyword);
        return ResponseEntity.ok(ApiResponse.<List<HrEvaluationSummaryItem>>builder()
                .code(200)
                .message("Lấy danh sách tổng hợp đánh giá thành công")
                .data(result)
                .build());
    }
}
