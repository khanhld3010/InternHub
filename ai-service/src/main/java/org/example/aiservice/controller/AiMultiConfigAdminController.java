package org.example.aiservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.aiservice.dto.request.TestProfileRequest;
import org.example.aiservice.dto.request.UpdateFeatureProfileRequest;
import org.example.aiservice.dto.request.UpdateGlobalKeyRequest;
import org.example.aiservice.dto.response.AiMultiConfigResponse;
import org.example.aiservice.dto.response.TestProfileResponse;
import org.example.aiservice.service.AiMultiConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/admin/multi-config")
@RequiredArgsConstructor
public class AiMultiConfigAdminController {

    private final AiMultiConfigService multiConfigService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiMultiConfigResponse> getMultiConfig() {
        return ResponseEntity.ok(multiConfigService.getMultiConfig());
    }

    @PutMapping("/global-key")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateGlobalKey(
            @Valid @RequestBody UpdateGlobalKeyRequest request,
            @AuthenticationPrincipal String adminUserId
    ) {
        String userId = (adminUserId != null && !adminUserId.isBlank()) ? adminUserId : "ADMIN";
        multiConfigService.updateGlobalKey(request, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/profiles/{featureKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiMultiConfigResponse.FeatureProfileDto> updateProfile(
            @PathVariable String featureKey,
            @Valid @RequestBody UpdateFeatureProfileRequest request,
            @AuthenticationPrincipal String adminUserId
    ) {
        String userId = (adminUserId != null && !adminUserId.isBlank()) ? adminUserId : "ADMIN";
        return ResponseEntity.ok(multiConfigService.updateProfile(featureKey, request, userId));
    }

    @PostMapping("/profiles/{featureKey}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestProfileResponse> testProfile(
            @PathVariable String featureKey,
            @Valid @RequestBody TestProfileRequest request
    ) {
        return ResponseEntity.ok(multiConfigService.testProfile(featureKey, request));
    }
}
