package org.example.aiservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.aiservice.dto.request.PreviewPromptTemplateRequest;
import org.example.aiservice.dto.request.UpsertPromptTemplateRequest;
import org.example.aiservice.dto.response.ApiResponse;
import org.example.aiservice.dto.response.PreviewPromptTemplateResponse;
import org.example.aiservice.dto.response.PromptTemplateResponse;
import org.example.aiservice.service.AiPromptTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/admin/prompt-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AiPromptTemplateAdminController {

    private final AiPromptTemplateService promptTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PromptTemplateResponse>>> getActiveTemplates(
            @RequestParam(required = false) String featureGroup
    ) {
        List<PromptTemplateResponse> data = promptTemplateService.getActiveTemplates(featureGroup);
        return ResponseEntity.ok(ApiResponse.<List<PromptTemplateResponse>>builder()
                .code(200)
                .message("Lấy danh sách Prompt Templates thành công")
                .data(data)
                .build());
    }

    @GetMapping("/{templateCode}/versions")
    public ResponseEntity<ApiResponse<List<PromptTemplateResponse>>> getTemplateVersions(
            @PathVariable String templateCode
    ) {
        List<PromptTemplateResponse> data = promptTemplateService.getTemplateVersions(templateCode);
        return ResponseEntity.ok(ApiResponse.<List<PromptTemplateResponse>>builder()
                .code(200)
                .message("Lấy lịch sử phiên bản template thành công")
                .data(data)
                .build());
    }

    @PutMapping("/{templateCode}")
    public ResponseEntity<ApiResponse<PromptTemplateResponse>> upsertTemplate(
            @PathVariable String templateCode,
            @Valid @RequestBody UpsertPromptTemplateRequest request
    ) {
        PromptTemplateResponse data = promptTemplateService.upsertTemplate(templateCode, request);
        return ResponseEntity.ok(ApiResponse.<PromptTemplateResponse>builder()
                .code(200)
                .message("Cập nhật Prompt Template thành công")
                .data(data)
                .build());
    }

    @PostMapping("/{templateCode}/versions/{version}/activate")
    public ResponseEntity<ApiResponse<PromptTemplateResponse>> activateVersion(
            @PathVariable String templateCode,
            @PathVariable int version
    ) {
        PromptTemplateResponse data = promptTemplateService.activateVersion(templateCode, version);
        return ResponseEntity.ok(ApiResponse.<PromptTemplateResponse>builder()
                .code(200)
                .message("Kích hoạt phiên bản thành công")
                .data(data)
                .build());
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<PreviewPromptTemplateResponse>> previewTemplate(
            @Valid @RequestBody PreviewPromptTemplateRequest request
    ) {
        PreviewPromptTemplateResponse data = promptTemplateService.previewTemplate(request);
        return ResponseEntity.ok(ApiResponse.<PreviewPromptTemplateResponse>builder()
                .code(200)
                .message("Nội suy xem trước template thành công")
                .data(data)
                .build());
    }
}
