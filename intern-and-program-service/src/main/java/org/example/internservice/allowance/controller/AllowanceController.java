package org.example.internservice.allowance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.allowance.dto.request.AdjustAllowanceRequest;
import org.example.internservice.allowance.dto.request.BatchApproveAllowanceRequest;
import org.example.internservice.allowance.dto.request.RetroAdjustmentRequest;
import org.example.internservice.allowance.dto.request.UnlockPeriodRequest;
import org.example.internservice.allowance.dto.response.AllowanceAuditLogResponse;
import org.example.internservice.allowance.dto.response.AllowanceRecordResponse;
import org.example.internservice.allowance.dto.response.BatchApproveResultResponse;
import org.example.internservice.allowance.entity.enums.AllowanceStatus;
import org.example.internservice.allowance.service.AllowanceExcelExportHelper;
import org.example.internservice.allowance.service.AllowanceService;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/allowances")
@RequiredArgsConstructor
@Slf4j
public class AllowanceController {

    private final AllowanceService allowanceService;

    @PostMapping("/calculate-preview")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AllowanceRecordResponse>>> calculatePreview(
            @RequestParam String month) {
        List<AllowanceRecordResponse> previews = allowanceService.calculatePreview(month);
        return ResponseEntity.ok(ApiResponse.success(previews));
    }

    @PostMapping("/batch-save")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AllowanceRecordResponse>>> batchSave(
            @RequestParam String month,
            @AuthenticationPrincipal CustomUserDetails user) {
        List<AllowanceRecordResponse> saved = allowanceService.batchSave(month, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PutMapping("/{id}/adjust")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AllowanceRecordResponse>> adjustAllowance(
            @PathVariable Long id,
            @Valid @RequestBody AdjustAllowanceRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        AllowanceRecordResponse response = allowanceService.adjustAllowance(id, request, user.getUserId(), user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/batch-approve")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BatchApproveResultResponse>> batchApprove(
            @Valid @RequestBody BatchApproveAllowanceRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        BatchApproveResultResponse result = allowanceService.batchApprove(request, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/period/{month}/unlock")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlockPeriod(
            @PathVariable String month,
            @Valid @RequestBody UnlockPeriodRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        allowanceService.unlockPeriod(month, request, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AllowanceRecordResponse>> markPaid(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        AllowanceRecordResponse response = allowanceService.markPaid(id, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/retro-adjustment")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AllowanceRecordResponse>> createRetroAdjustment(
            @Valid @RequestBody RetroAdjustmentRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        AllowanceRecordResponse response = allowanceService.createRetroAdjustment(request, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my-history")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<List<AllowanceRecordResponse>>> getMyHistory(
            @AuthenticationPrincipal CustomUserDetails user) {
        List<AllowanceRecordResponse> history = allowanceService.getMyHistory(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AllowanceRecordResponse>>> getAllowancesByPeriod(
            @RequestParam String month,
            @RequestParam(required = false) AllowanceStatus status) {
        List<AllowanceRecordResponse> records = allowanceService.getAllowancesByPeriod(month, status);
        return ResponseEntity.ok(ApiResponse.success(records));
    }

    @GetMapping("/intern/{internId}")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AllowanceRecordResponse>>> getInternHistory(
            @PathVariable Long internId) {
        List<AllowanceRecordResponse> history = allowanceService.getInternHistory(internId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/{id}/audit-logs")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AllowanceAuditLogResponse>>> getAuditLogs(
            @PathVariable Long id) {
        List<AllowanceAuditLogResponse> logs = allowanceService.getAuditLogs(id);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> exportToExcel(@RequestParam String month) throws IOException {
        List<AllowanceRecordResponse> records = allowanceService.getAllowancesByPeriod(month, null);
        // Chi xuat cac ban ghi da duyet hoac da chi tra
        List<AllowanceRecordResponse> exportable = records.stream()
                .filter(r -> r.getStatus() == AllowanceStatus.APPROVED || r.getStatus() == AllowanceStatus.PAID)
                .toList();

        if (exportable.isEmpty()) {
            throw new BadRequestException("Không có bản ghi nào ở trạng thái APPROVED hoặc PAID để xuất Excel trong kỳ " + month);
        }

        byte[] excelBytes = AllowanceExcelExportHelper.exportToExcel(exportable, month);
        String filename = "Bang_Phu_Cap_" + month + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}
