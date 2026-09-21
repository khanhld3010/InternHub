package org.example.employeeservice.intern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.intern.dto.response.DocumentResponse;
import org.example.employeeservice.intern.service.InternDocumentService;
import org.example.employeeservice.system.audit.annotation.Auditable;
import org.example.employeeservice.system.audit.entity.AuditAction;
import org.example.employeeservice.system.audit.entity.AuditModule;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/employees/interns")
@RequiredArgsConstructor
@Tag(name = "Intern Document Controller", description = "Quản lý tải lên tài liệu ứng tuyển (CV, Đơn xin thực tập)")
public class InternDocumentController {

    private final InternDocumentService internDocumentService;

    @Operation(summary = "Tải lên CV hoặc đơn xin thực tập (Public Endpoint - không cần đăng nhập)")
    @Auditable(action = AuditAction.UPLOAD_DOCUMENT, module = AuditModule.DOCUMENT, description = "Tải lên hồ sơ tài liệu/CV thực tập sinh")
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
}
