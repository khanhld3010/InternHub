package org.example.aiservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.dto.request.AddKeyVaultRequest;
import org.example.aiservice.dto.response.AiKeyVaultDto;
import org.example.aiservice.dto.response.TestKeyVaultResponse;
import org.example.aiservice.service.AiKeyVaultAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/admin/vault")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AiKeyVaultAdminController {

    private final AiKeyVaultAdminService vaultAdminService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllKeys() {
        List<AiKeyVaultDto> keys = vaultAdminService.getAllKeys();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Lấy danh sách khóa API thành công",
                "data", keys
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addKey(
            @Valid @RequestBody AddKeyVaultRequest request,
            Authentication authentication
    ) {
        String adminUser = authentication != null ? authentication.getName() : "ADMIN";
        AiKeyVaultDto created = vaultAdminService.addKey(request, adminUser);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Thêm khóa API vào kho thành công",
                "data", created
        ));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testKey(@PathVariable Long id) {
        TestKeyVaultResponse response = vaultAdminService.testKey(id);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", response.getMessage(),
                "data", response
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateKey(
            @PathVariable Long id,
            @Valid @RequestBody org.example.aiservice.dto.request.UpdateKeyVaultRequest request,
            Authentication authentication
    ) {
        String adminUser = authentication != null ? authentication.getName() : "ADMIN";
        AiKeyVaultDto updated = vaultAdminService.updateKey(id, request, adminUser);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Cập nhật thông tin khóa thành công",
                "data", updated
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteKey(@PathVariable Long id) {
        vaultAdminService.deleteKey(id);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Xóa khóa API khỏi kho thành công"
        ));
    }
}
