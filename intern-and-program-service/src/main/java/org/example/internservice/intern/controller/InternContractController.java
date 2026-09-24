package org.example.internservice.intern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.intern.dto.request.UploadContractRequest;
import org.example.internservice.intern.dto.response.ContractResponse;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.intern.service.InternContractService;
import org.example.internservice.system.audit.annotation.Auditable;
import org.example.internservice.system.audit.entity.AuditAction;
import org.example.internservice.system.audit.entity.AuditModule;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/interns")
@RequiredArgsConstructor
@Tag(name = "Intern Contract Controller", description = "Quản lý hợp đồng thực tập (Tải lên, tra cứu, tải về)")
public class InternContractController {

    private final InternContractService internContractService;

    @Operation(summary = "Tải lên hợp đồng thực tập (Chỉ dành cho HR và Admin)")
    @Auditable(action = AuditAction.UPLOAD_CONTRACT, module = AuditModule.DOCUMENT, description = "Tải lên hợp đồng thực tập cho ứng viên")
    @PostMapping(value = "/{internCode}/contracts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ContractResponse>> uploadContract(
            @PathVariable("internCode") String internCode,
            @RequestParam("file") MultipartFile file,
            @Valid @ModelAttribute UploadContractRequest request,
            Authentication authentication
    ) {
        String uploadedBy = (authentication != null) ? authentication.getName() : "HR";
        log.info("API Upload hợp đồng: internCode={}, title={}, uploadedBy={}", internCode, request.getContractTitle(), uploadedBy);

        ContractResponse response = internContractService.uploadContract(internCode, file, request, uploadedBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Tải lên hợp đồng thực tập thành công", response));
    }

    @Operation(summary = "Lấy danh sách hợp đồng của một thực tập sinh")
    @GetMapping("/{internCode}/contracts")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR', 'INTERN')")
    public ResponseEntity<ApiResponse<List<ContractResponse>>> getContractsByInternCode(
            @PathVariable("internCode") String internCode
    ) {
        log.info("API Lấy danh sách hợp đồng: internCode={}", internCode);
        List<ContractResponse> contracts = internContractService.getContractsByInternCode(internCode);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách hợp đồng thành công", contracts));
    }

    @Operation(summary = "Tải hoặc xem tệp hợp đồng đính kèm")
    @GetMapping("/contracts/{contractId}/download")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR', 'INTERN')")
    public ResponseEntity<Resource> downloadContract(
            @PathVariable("contractId") Long contractId,
            @RequestParam(name = "disposition", defaultValue = "inline") String disposition
    ) {
        log.info("API Tải/Xem hợp đồng: contractId={}, disposition={}", contractId, disposition);
        DocumentDownloadDto downloadDto = internContractService.loadContractForDownload(contractId);

        String originalFileName = downloadDto.getOriginalFileName();
        String contentType = downloadDto.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_PDF_VALUE;
        }

        String encodedFilename = URLEncoder.encode(originalFileName, StandardCharsets.UTF_8).replace("+", "%20");
        String dispositionType = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        String contentDispositionValue = String.format("%s; filename=\"%s\"; filename*=UTF-8''%s",
                dispositionType, originalFileName.replace("\"", "\\\""), encodedFilename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionValue)
                .body(downloadDto.getResource());
    }
}
