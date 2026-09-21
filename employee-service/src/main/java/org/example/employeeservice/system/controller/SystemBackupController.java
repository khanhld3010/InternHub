package org.example.employeeservice.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.common.dto.response.PageResponse;
import org.example.employeeservice.system.dto.request.BackupFilterRequest;
import org.example.employeeservice.system.dto.response.BackupResponse;
import org.example.employeeservice.system.entity.BackupType;
import org.example.employeeservice.system.service.SystemBackupService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/system/backups")
@RequiredArgsConstructor
@Tag(name = "System Backup Controller", description = "Quản lý sao lưu dữ liệu hệ thống định kỳ và thủ công (TM-8)")
@PreAuthorize("hasRole('ADMIN')")
public class SystemBackupController {

    private final SystemBackupService systemBackupService;

    @Operation(summary = "Kích hoạt sao lưu dữ liệu thủ công tức thời (On-demand Backup)")
    @PostMapping
    public ResponseEntity<ApiResponse<BackupResponse>> triggerManualBackup(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "ADMIN";
        log.info("Admin {} yêu cầu kích hoạt sao lưu thủ công", username);

        BackupResponse response = systemBackupService.triggerBackup(BackupType.MANUAL, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Tiến trình sao lưu dữ liệu đã được khởi chạy thành công", response));
    }

    @Operation(summary = "Lấy danh sách lịch sử các phiên sao lưu (Hỗ trợ phân trang và lọc)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BackupResponse>>> getBackupHistory(
            @ModelAttribute BackupFilterRequest filterRequest,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC, size = 10) Pageable pageable
    ) {
        log.info("API: Truy vấn danh sách lịch sử sao lưu (page={}, size={})", pageable.getPageNumber(), pageable.getPageSize());
        PageResponse<BackupResponse> response = systemBackupService.getBackupHistory(filterRequest, pageable);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách lịch sử sao lưu thành công", response));
    }

    @Operation(summary = "Tải xuống file bản sao lưu an toàn (.sql.gz)")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadBackup(@PathVariable("id") Long id) {
        log.info("API: Yêu cầu tải bản sao lưu ID={}", id);
        Resource resource = systemBackupService.downloadBackupFile(id);
        String fileName = systemBackupService.getBackupFileName(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/gzip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @Operation(summary = "Xóa một bản sao lưu và loại bỏ file vật lý tương ứng")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBackup(@PathVariable("id") Long id) {
        log.info("API: Yêu cầu xóa bản sao lưu ID={}", id);
        systemBackupService.deleteBackup(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Xóa bản sao lưu thành công", null));
    }
}
