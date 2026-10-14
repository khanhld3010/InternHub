package org.example.aiservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.aiservice.dto.request.UpdateAiConfigRequest;
import org.example.aiservice.dto.response.AiConfigResponse;
import org.example.aiservice.dto.response.ApiResponse;
import org.example.aiservice.service.AiConfigAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/admin/config")
@RequiredArgsConstructor
public class AiConfigAdminController {

    private final AiConfigAdminService adminService;

    @GetMapping
    @PreAuthorize("hasAuthority('AI_CONFIG_MANAGE') or hasRole('ADMIN')")
    public ApiResponse<AiConfigResponse> getConfig() {
        return ApiResponse.success(adminService.getConfig());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('AI_CONFIG_MANAGE') or hasRole('ADMIN')")
    public ApiResponse<AiConfigResponse> updateConfig(
            @Valid @RequestBody UpdateAiConfigRequest request,
            @AuthenticationPrincipal String userId
    ) {
        return ApiResponse.success(adminService.updateConfig(request, userId));
    }
}
