package org.example.internservice.intern.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.intern.dto.request.ConfirmContractRequest;
import org.example.internservice.intern.dto.request.ContractFeedbackRequest;
import org.example.internservice.intern.dto.request.ContractStorageDtos;
import org.example.internservice.intern.dto.request.RejectContractRequest;
import org.example.internservice.intern.dto.request.StorageBusinessDtos;
import org.example.internservice.intern.dto.request.TerminateContractRequest;
import org.example.internservice.intern.dto.request.UploadContractRequest;
import org.example.internservice.intern.dto.response.ContractResponse;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.intern.service.InternContractService;
import org.example.internservice.security.CustomUserDetails;
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
import org.springframework.web.bind.annotation.RequestBody;
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
@Tag(name = "Intern Contract Controller", description = "Quản lý hợp đồng thực tập (Tải lên qua S3, tra cứu tập trung, phản hồi thắc mắc, gia hạn, chấm dứt)")
public class InternContractController {

    private final InternContractService internContractService;

    @Operation(summary = "Xin cấp Presigned URL để upload hợp đồng trực tiếp lên S3/MinIO (Direct-to-S3)")
    @PostMapping("/{internCode}/contracts/upload-url")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<StorageBusinessDtos.RequestUploadUrlResponse>> requestContractUploadUrl(
            @PathVariable("internCode") String internCode,
            @Valid @RequestBody ContractStorageDtos.RequestContractUploadUrlRequest request
    ) {
        log.info("API Xin presigned S3 upload URL cho hợp đồng intern: {}", internCode);
        StorageBusinessDtos.RequestUploadUrlResponse response = internContractService.requestContractUploadUrl(internCode, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Tạo URL tải lên S3 thành công", response));
    }

    @Operation(summary = "Xác nhận upload hợp đồng thành công lên S3 để ghi nhận dữ liệu vào hệ thống")
    @Auditable(action = AuditAction.UPLOAD_CONTRACT, module = AuditModule.DOCUMENT, description = "Xác nhận upload S3 hợp đồng thực tập")
    @PostMapping("/{internCode}/contracts/confirm-upload")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ContractResponse>> confirmContractUpload(
            @PathVariable("internCode") String internCode,
            @Valid @RequestBody ContractStorageDtos.ConfirmContractUploadRequest request,
            Authentication authentication
    ) {
        String uploadedBy = (authentication != null) ? authentication.getName() : "HR";
        log.info("API Confirm upload S3 hợp đồng: internCode={}, title={}, uploadedBy={}", internCode, request.getContractTitle(), uploadedBy);
        ContractResponse response = internContractService.confirmContractUpload(internCode, request, uploadedBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Tải lên và khởi tạo hợp đồng thành công", response));
    }

    @Operation(summary = "Lấy Presigned URL xem trước hợp đồng trực tiếp từ S3 (Loại bỏ lỗi 403 Forbidden)")
    @GetMapping("/contracts/{contractId}/view-url")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR', 'INTERN')")
    public ResponseEntity<ApiResponse<ContractStorageDtos.ViewContractUrlResponse>> getContractViewUrl(
            @PathVariable("contractId") Long contractId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        ContractStorageDtos.ViewContractUrlResponse response = internContractService.getContractViewUrl(contractId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy URL xem hợp đồng thành công", response));
    }

    @Operation(summary = "Lấy danh sách TOÀN BỘ hợp đồng trong công ty dành cho HR (Contract Hub)")
    @GetMapping("/contracts/all")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ContractResponse>>> getAllContracts() {
        log.info("API HR Lấy danh sách toàn bộ hợp đồng công ty");
        List<ContractResponse> contracts = internContractService.getAllContracts();
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy toàn bộ danh sách hợp đồng thành công", contracts));
    }

    @Operation(summary = "Tải lên hợp đồng thực tập bằng Multipart form (Fallback)")
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
        log.info("API Upload hợp đồng multipart: internCode={}, title={}, uploadedBy={}", internCode, request.getContractTitle(), uploadedBy);
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

    @Operation(summary = "Lấy danh sách hợp đồng cá nhân của thực tập sinh đang đăng nhập")
    @GetMapping("/contracts/my-contracts")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<List<ContractResponse>>> getMyContracts(Authentication authentication) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        List<ContractResponse> contracts = internContractService.getMyContracts(userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách hợp đồng cá nhân thành công", contracts));
    }

    @Operation(summary = "Lấy hợp đồng đang chờ ký hoặc hiệu lực gần nhất của thực tập sinh đang đăng nhập")
    @GetMapping("/contracts/my-contracts/active")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<ContractResponse>> getMyActiveContract(Authentication authentication) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        ContractResponse response = internContractService.getMyActiveContract(userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy hợp đồng hiệu lực gần nhất thành công", response));
    }

    @Operation(summary = "Xem chi tiết hợp đồng thực tập theo ID")
    @GetMapping("/contracts/{contractId}")
    @PreAuthorize("hasAnyRole('INTERN', 'HR', 'ADMIN', 'MENTOR')")
    public ResponseEntity<ApiResponse<ContractResponse>> getContractById(
            @PathVariable("contractId") Long contractId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        ContractResponse response = internContractService.getContractById(contractId, userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy chi tiết hợp đồng thành công", response));
    }

    @Operation(summary = "Xác nhận ký cam kết hợp đồng thực tập điện tử")
    @Auditable(action = AuditAction.CONFIRM_CONTRACT, module = AuditModule.DOCUMENT, description = "Thực tập sinh xác nhận ký hợp đồng")
    @PostMapping("/contracts/{contractId}/confirm")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<ContractResponse>> confirmContract(
            @PathVariable("contractId") Long contractId,
            @Valid @RequestBody ConfirmContractRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        ContractResponse response = internContractService.confirmContract(contractId, request, userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Xác nhận ký hợp đồng thực tập thành công", response));
    }

    @Operation(summary = "Thực tập sinh gửi thắc mắc liên hệ HR (scan mờ, sai điều khoản)")
    @PostMapping("/contracts/{contractId}/feedback")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<ContractResponse>> submitFeedback(
            @PathVariable("contractId") Long contractId,
            @Valid @RequestBody ContractFeedbackRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("API Gửi thắc mắc hợp đồng ID: {}, user={}", contractId, userDetails != null ? userDetails.getUsername() : "null");
        ContractResponse response = internContractService.submitFeedback(contractId, request, userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Đã gửi phản hồi thắc mắc tới HR thành công", response));
    }

    @Operation(summary = "Chấm dứt hợp đồng trước hạn (Chỉ HR/Admin, bảo toàn lịch sử kiểm toán)")
    @PostMapping("/contracts/{contractId}/terminate")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ContractResponse>> terminateContract(
            @PathVariable("contractId") Long contractId,
            @Valid @RequestBody TerminateContractRequest request,
            Authentication authentication
    ) {
        String terminatedBy = (authentication != null) ? authentication.getName() : "HR";
        log.info("API Chấm dứt hợp đồng ID: {}, user={}", contractId, terminatedBy);
        ContractResponse response = internContractService.terminateContract(contractId, request, terminatedBy);
        return ResponseEntity.ok(ApiResponse.success(200, "Đã chấm dứt hợp đồng trước hạn hợp lệ", response));
    }

    @Operation(summary = "HR gửi thông báo nhắc nhở ký hợp đồng cho TTS")
    @PostMapping("/contracts/{contractId}/remind")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendContractReminder(
            @PathVariable("contractId") Long contractId,
            Authentication authentication
    ) {
        String sentBy = (authentication != null) ? authentication.getName() : "HR";
        log.info("API Gửi nhắc nhở ký hợp đồng ID: {}, user={}", contractId, sentBy);
        internContractService.sendContractReminder(contractId, sentBy);
        return ResponseEntity.ok(ApiResponse.success(200, "Đã gửi nhắc nhở ký hợp đồng tới thực tập sinh", null));
    }

    @Operation(summary = "Từ chối ký hợp đồng thực tập kèm lý do")
    @Auditable(action = AuditAction.REJECT_CONTRACT, module = AuditModule.DOCUMENT, description = "Thực tập sinh từ chối ký hợp đồng")
    @PostMapping("/contracts/{contractId}/reject")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<ContractResponse>> rejectContract(
            @PathVariable("contractId") Long contractId,
            @Valid @RequestBody RejectContractRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = extractUserDetails(authentication);
        log.info("API Từ chối ký hợp đồng ID: {}, user={}", contractId, userDetails != null ? userDetails.getUsername() : "null");
        ContractResponse response = internContractService.rejectContract(contractId, request, userDetails);
        return ResponseEntity.ok(ApiResponse.success(200, "Đã ghi nhận từ chối hợp đồng thực tập", response));
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

    private CustomUserDetails extractUserDetails(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        return null;
    }
}
