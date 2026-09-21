package org.example.employeeservice.system.audit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.common.dto.response.PageResponse;
import org.example.employeeservice.system.audit.dto.request.AuditLogFilterRequest;
import org.example.employeeservice.system.audit.dto.response.AuditLogDetailResponse;
import org.example.employeeservice.system.audit.dto.response.AuditLogResponse;
import org.example.employeeservice.system.audit.dto.response.AuditLogStatsResponse;
import org.example.employeeservice.system.audit.service.AuditLogService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/api/system/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Log Controller", description = "Quản trị nhật ký hoạt động và kiểm toán hệ thống (TM-9)")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Lấy danh sách nhật ký hoạt động có phân trang và bộ lọc đa chiều")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
            @ModelAttribute AuditLogFilterRequest filterRequest,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC, size = 20) Pageable pageable
    ) {
        log.info("Admin truy vấn danh sách audit logs (keyword={}, module={}, action={}, status={}, page={}, size={})",
                filterRequest.getKeyword(), filterRequest.getModule(), filterRequest.getAction(), filterRequest.getStatus(),
                pageable.getPageNumber(), pageable.getPageSize());

        PageResponse<AuditLogResponse> response = auditLogService.getAuditLogs(filterRequest, pageable);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách nhật ký hoạt động thành công", response));
    }

    @Operation(summary = "Lấy thông tin chi tiết một bản ghi nhật ký kiểm toán")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditLogDetailResponse>> getAuditLogDetail(@PathVariable("id") Long id) {
        log.info("Admin xem chi tiết audit log ID={}", id);
        AuditLogDetailResponse response = auditLogService.getAuditLogDetail(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy thông tin chi tiết nhật ký thành công", response));
    }

    @Operation(summary = "Lấy dữ liệu thống kê nhật ký hoạt động trong ngày hôm nay")
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AuditLogStatsResponse>> getAuditStatistics() {
        log.info("Admin yêu cầu lấy dữ liệu thống kê audit logs");
        AuditLogStatsResponse response = auditLogService.getAuditStatistics();
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy dữ liệu thống kê nhật ký thành công", response));
    }

    @Operation(summary = "Xuất danh sách nhật ký hoạt động ra tệp định dạng CSV")
    @GetMapping("/export")
    public ResponseEntity<Resource> exportAuditLogsCsv(@ModelAttribute AuditLogFilterRequest filterRequest) {
        log.info("Admin yêu cầu xuất tệp CSV danh sách audit logs");
        byte[] csvData = auditLogService.exportAuditLogsCsv(filterRequest);
        ByteArrayResource resource = new ByteArrayResource(csvData);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "audit_logs_" + timestamp + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
