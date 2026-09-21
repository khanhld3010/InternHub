package org.example.employeeservice.intern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.intern.dto.request.ReviewDocumentRequest;
import org.example.employeeservice.intern.dto.response.DocumentDownloadDto;
import org.example.employeeservice.intern.dto.response.DocumentResponse;
import org.example.employeeservice.intern.service.InternDocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/employees/interns")
@RequiredArgsConstructor
@Tag(name = "Intern Document Controller", description = "Quản lý tải lên tài liệu ứng tuyển (CV, Đơn xin thực tập)")
public class InternDocumentController {

    private final InternDocumentService internDocumentService;

    @Operation(summary = "Tải lên CV hoặc đơn xin thực tập (Public Endpoint - không cần đăng nhập)")
    @PostMapping(value = "/{internCode}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @PathVariable("internCode") String internCode,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType
    ) {
        log.info("API Upload tài liệu được gọi: internCode={}, documentType={}, fileName={}",
                internCode, documentType, file != null ? file.getOriginalFilename() : "null");

        DocumentResponse response = internDocumentService.uploadDocument(internCode, file, documentType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Tải lên tài liệu thành công", response));
    }

    @GetMapping("/{internCode}/documents")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocumentsByInternCode(
            @PathVariable("internCode") String internCode
    ) {
        log.info("API Lấy danh sách tài liệu cho thực tập sinh: {}", internCode);
        List<DocumentResponse> documents = internDocumentService.getDocumentsByInternCode(internCode);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách tài liệu thành công", documents));
    }

    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable("documentId") Long documentId,
            @RequestParam(name = "disposition", defaultValue = "inline") String disposition
    ) {
        log.info("API Tải/Xem tài liệu: documentId={}, disposition={}", documentId, disposition);
        DocumentDownloadDto downloadDto = internDocumentService.loadDocumentForDownload(documentId);

        String originalFileName = downloadDto.getOriginalFileName();
        String contentType = downloadDto.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        // Cấu hình header Content-Disposition chuẩn RFC 5987 (hỗ trợ tiếng Việt có dấu)
        String encodedFilename = URLEncoder.encode(originalFileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String dispositionType = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        String contentDispositionValue = String.format("%s; filename=\"%s\"; filename*=UTF-8''%s",
                dispositionType, originalFileName.replace("\"", "\\\""), encodedFilename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionValue)
                .body(downloadDto.getResource());
    }

    @PatchMapping("/documents/{documentId}/review")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DocumentResponse>> reviewDocument(
            @PathVariable("documentId") Long documentId,
            @Valid @RequestBody ReviewDocumentRequest request
    ) {
        log.info("API Xét duyệt tài liệu: documentId={}, status={}, rejectionReason={}",
                documentId, request.getStatus(), request.getRejectionReason());

        DocumentResponse response = internDocumentService.reviewDocument(documentId, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật trạng thái xét duyệt tài liệu thành công", response));
    }
}
