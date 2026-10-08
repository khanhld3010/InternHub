package org.example.internservice.contract.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.contract.dto.request.ChangeRequestDto;
import org.example.internservice.contract.dto.request.ConfirmRevisionRequest;
import org.example.internservice.contract.dto.request.CreateContractDraftRequest;
import org.example.internservice.contract.dto.request.SignContractRequest;
import org.example.internservice.contract.dto.response.DynamicContractResponse;
import org.example.internservice.contract.service.DynamicContractService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class DynamicContractController {

    private final DynamicContractService contractService;

    // --- HR ENDPOINTS ---

    @PostMapping("/drafts")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DynamicContractResponse>> createDraft(@Valid @RequestBody CreateContractDraftRequest request) {
        String username = getUsername();
        DynamicContractResponse response = contractService.createDraft(request, username != null ? username : "HR");
        return ResponseEntity.ok(ApiResponse.success(200, "Tạo hợp đồng nháp thành công", response));
    }

    @GetMapping("/drafts/{id}/preview")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> previewDraft(@PathVariable Long id) {
        Map<String, String> preview = contractService.previewDraft(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Tải bản xem trước hợp đồng thành công", preview));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DynamicContractResponse>> sendContract(@PathVariable Long id) {
        String username = getUsername();
        DynamicContractResponse response = contractService.sendContract(id, username != null ? username : "HR");
        return ResponseEntity.ok(ApiResponse.success(200, "Phát hành hợp đồng cho thực tập sinh thành công", response));
    }

    @PostMapping("/{id}/revisions")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DynamicContractResponse>> createRevision(
            @PathVariable Long id,
            @Valid @RequestBody CreateContractDraftRequest request) {
        String username = getUsername();
        DynamicContractResponse response = contractService.createRevision(id, request, username != null ? username : "HR");
        return ResponseEntity.ok(ApiResponse.success(200, "Tạo bản sửa đổi mới thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<DynamicContractResponse>>> getAllContracts() {
        java.util.List<DynamicContractResponse> response = contractService.getAllContracts();
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách toàn bộ hợp đồng thành công", response));
    }

    // --- INTERN ENDPOINTS ---

    @GetMapping("/intern/me")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<DynamicContractResponse>> getMyContract() {
        Long internUserId = getUserId();
        DynamicContractResponse response = contractService.getMyContract(internUserId);
        return ResponseEntity.ok(ApiResponse.success(200, "Lấy thông tin hợp đồng của bạn thành công", response));
    }

    @GetMapping("/intern/{contractId}/revisions/{revisionId}/preview")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> previewMyRevision(
            @PathVariable Long contractId,
            @PathVariable Long revisionId) {
        Long internUserId = getUserId();
        Map<String, String> preview = contractService.previewRevision(contractId, revisionId, internUserId);
        return ResponseEntity.ok(ApiResponse.success(200, "Tải bản xem trước hợp đồng thành công", preview));
    }

    @PostMapping("/intern/{contractId}/revisions/{revisionId}/request-changes")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<Void>> requestChanges(
            @PathVariable Long contractId,
            @PathVariable Long revisionId,
            @Valid @RequestBody ChangeRequestDto request) {
        Long internUserId = getUserId();
        contractService.requestChanges(contractId, revisionId, request, internUserId);
        return ResponseEntity.ok(ApiResponse.success(200, "Gửi yêu cầu điều chỉnh thông tin thành công", null));
    }

    @PostMapping("/intern/{contractId}/revisions/{revisionId}/confirm")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<Void>> confirmRevision(
            @PathVariable Long contractId,
            @PathVariable Long revisionId,
            @Valid @RequestBody ConfirmRevisionRequest request) {
        Long internUserId = getUserId();
        contractService.confirmRevision(contractId, revisionId, request, internUserId);
        return ResponseEntity.ok(ApiResponse.success(200, "Xác nhận thông tin hợp đồng thành công", null));
    }

    @PostMapping("/intern/{contractId}/revisions/{revisionId}/sign")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<Void>> signRevision(
            @PathVariable Long contractId,
            @PathVariable Long revisionId,
            @Valid @RequestBody SignContractRequest request,
            HttpServletRequest httpRequest) {
        Long internUserId = getUserId();
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        contractService.signRevision(contractId, revisionId, request, internUserId, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(200, "Ký hợp đồng điện tử thành công", null));
    }

    @GetMapping("/intern/{contractId}/revisions/{revisionId}/pdf")
    @PreAuthorize("hasAnyRole('INTERN', 'HR', 'ADMIN')")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long contractId,
            @PathVariable Long revisionId) {
        Long internUserId = getUserId();
        byte[] pdfBytes = contractService.getContractPdfBytes(contractId, revisionId, internUserId);
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline()
                .filename("contract_" + contractId + "_rev" + revisionId + ".pdf")
                .build());
        headers.setContentLength(pdfBytes.length);
        
        return new ResponseEntity<>(pdfBytes, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/intern/me/pdf")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<byte[]> downloadMyPdf() {
        Long internUserId = getUserId();
        byte[] pdfBytes = contractService.getMyContractPdfBytes(internUserId);
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline()
                .filename("my_contract.pdf")
                .build());
        headers.setContentLength(pdfBytes.length);
        
        return new ResponseEntity<>(pdfBytes, headers, org.springframework.http.HttpStatus.OK);
    }

    private Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new SecurityException("Không thể xác định thông tin tài khoản người dùng");
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUsername();
        }
        return auth != null ? auth.getName() : null;
    }
}
