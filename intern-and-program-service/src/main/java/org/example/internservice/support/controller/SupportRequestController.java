package org.example.internservice.support.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.security.CustomUserDetails;
import org.example.internservice.support.dto.request.CreateSupportRequestDto;
import org.example.internservice.support.dto.request.RespondSupportRequestDto;
import org.example.internservice.support.dto.response.SupportRequestResponse;
import org.example.internservice.support.entity.enums.SupportRequestStatus;
import org.example.internservice.support.entity.enums.SupportRequestType;
import org.example.internservice.support.service.SupportRequestService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support-requests")
@RequiredArgsConstructor
@Slf4j
public class SupportRequestController {

    private final SupportRequestService supportRequestService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> createRequest(
            @Valid @RequestPart("data") CreateSupportRequestDto dto,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment,
            @AuthenticationPrincipal CustomUserDetails user) {
        SupportRequestResponse response = supportRequestService.createRequest(dto, attachment, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<List<SupportRequestResponse>>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails user) {
        List<SupportRequestResponse> requests = supportRequestService.getMyRequests(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<SupportRequestResponse>>> getAllRequests(
            @RequestParam(required = false) SupportRequestStatus status,
            @RequestParam(required = false) SupportRequestType requestType) {
        List<SupportRequestResponse> requests = supportRequestService.getAllRequests(status, requestType);
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> cancelRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        SupportRequestResponse response = supportRequestService.cancelRequest(id, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> processRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        SupportRequestResponse response = supportRequestService.processRequest(id, user.getUserId(), user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping(value = "/{id}/respond", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SupportRequestResponse>> respondRequest(
            @PathVariable Long id,
            @Valid @RequestPart("data") RespondSupportRequestDto dto,
            @RequestPart(value = "responseAttachment", required = false) MultipartFile responseAttachment,
            @AuthenticationPrincipal CustomUserDetails user) {
        SupportRequestResponse response = supportRequestService.respondRequest(id, dto, responseAttachment, user.getUserId(), user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        Resource fileResource = supportRequestService.loadAttachmentFile(id, user.getUserId(), user.getRole(), false);
        String filename = supportRequestService.getAttachmentFilename(id, false);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileResource);
    }

    @GetMapping("/{id}/response-attachment")
    public ResponseEntity<Resource> downloadResponseAttachment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        Resource fileResource = supportRequestService.loadAttachmentFile(id, user.getUserId(), user.getRole(), true);
        String filename = supportRequestService.getAttachmentFilename(id, true);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileResource);
    }
}
