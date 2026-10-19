package org.example.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.client.GeminiApiClient;
import org.example.aiservice.dto.request.AddKeyVaultRequest;
import org.example.aiservice.dto.response.AiKeyVaultDto;
import org.example.aiservice.dto.response.TestKeyVaultResponse;
import org.example.aiservice.entity.AiKeyVaultEntry;
import org.example.aiservice.repository.AiGlobalConfigRepository;
import org.example.aiservice.repository.AiKeyVaultRepository;
import org.example.aiservice.security.AesGcmEncryptionUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiKeyVaultAdminService {

    private final AiKeyVaultRepository vaultRepository;
    private final AiGlobalConfigRepository globalConfigRepository;
    private final AesGcmEncryptionUtil encryptionUtil;
    private final GeminiApiClient geminiApiClient;

    @Transactional
    public List<AiKeyVaultDto> getAllKeys() {
        autoSeedDefaultKeyIfVaultEmpty();
        LocalDateTime now = LocalDateTime.now();
        return vaultRepository.findByIsDeletedFalseOrderByPriorityAscIdAsc().stream()
                .map(key -> toDto(key, now))
                .collect(Collectors.toList());
    }

    private void autoSeedDefaultKeyIfVaultEmpty() {
        if (vaultRepository.count() == 0) {
            globalConfigRepository.findFirstByOrderByIdAsc().ifPresent(global -> {
                if (global.getApiKeyEncrypted() != null && !global.getApiKeyEncrypted().isBlank()) {
                    String last4 = global.getApiKeyLast4() != null ? global.getApiKeyLast4() : "••••";
                    AiKeyVaultEntry defaultKey = AiKeyVaultEntry.builder()
                            .keyName("Google Gemini Khóa Chính (System Default)")
                            .provider(global.getProvider() != null ? global.getProvider() : "GEMINI")
                            .apiKeyEncrypted(global.getApiKeyEncrypted())
                            .apiKeyLast4(last4)
                            .apiKeyHash("SYSTEM_DEFAULT_SEED")
                            .priority(1)
                            .status(Boolean.FALSE.equals(global.getIsActive()) ? "DISABLED" : "ACTIVE")
                            .failureCount(0)
                            .createdBy("SYSTEM_AUTO_SEED")
                            .updatedBy("SYSTEM_AUTO_SEED")
                            .isDeleted(false)
                            .build();
                    vaultRepository.save(defaultKey);
                    log.info("[AiKeyVaultAdminService] Đã tự động seed khóa mặc định từ ai_global_configs vào ai_key_vault thành công.");
                }
            });
        }
    }

    @Transactional
    public AiKeyVaultDto addKey(AddKeyVaultRequest request, String adminUsername) {
        String rawKey = request.getApiKey() != null ? request.getApiKey().trim() : "";
        if (rawKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã khóa API không được để trống");
        }

        String keyHash = computeSha256(rawKey);
        if (vaultRepository.existsByApiKeyHashAndIsDeletedFalse(keyHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khóa API này đã tồn tại trong Kho khóa!");
        }

        // Test kết nối trước khi lưu trữ
        try {
            geminiApiClient.generateContent(
                    rawKey,
                    "gemini-3.5-flash-lite",
                    "You are a connection test agent. Reply with 'OK'.",
                    List.of(),
                    "Ping test",
                    new BigDecimal("0.10"),
                    10
            );
        } catch (Exception ex) {
            log.warn("Kiểm tra khóa API thất bại: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khóa API không hợp lệ hoặc bị Google từ chối: " + ex.getMessage());
        }

        String encryptedKey = encryptionUtil.encrypt(rawKey, 1);
        String last4 = rawKey.length() >= 4 ? rawKey.substring(rawKey.length() - 4) : "••••";

        AiKeyVaultEntry entry = AiKeyVaultEntry.builder()
                .keyName(request.getKeyName().trim())
                .provider(request.getProvider() != null ? request.getProvider().toUpperCase() : "GEMINI")
                .apiKeyEncrypted(encryptedKey)
                .apiKeyLast4(last4)
                .apiKeyHash(keyHash)
                .priority(request.getPriority() != null ? request.getPriority() : 1)
                .status("ACTIVE")
                .failureCount(0)
                .createdBy(adminUsername)
                .updatedBy(adminUsername)
                .isDeleted(false)
                .build();

        entry = vaultRepository.save(entry);
        return toDto(entry, LocalDateTime.now());
    }

    @Transactional
    public TestKeyVaultResponse testKey(Long id) {
        AiKeyVaultEntry entry = vaultRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa với ID: " + id));

        String rawKey = encryptionUtil.decrypt(entry.getApiKeyEncrypted(), 1);
        long start = System.currentTimeMillis();

        try {
            geminiApiClient.generateContent(
                    rawKey,
                    "gemini-3.5-flash-lite",
                    "Connection test",
                    List.of(),
                    "Ping test",
                    new BigDecimal("0.10"),
                    10
            );
            long latency = System.currentTimeMillis() - start;

            // Thành công -> Đưa về ACTIVE nếu đang bị lỗi
            entry.setStatus("ACTIVE");
            entry.setCooldownUntil(null);
            entry.setFailureCount(0);
            entry.setLastUsedAt(LocalDateTime.now());
            vaultRepository.save(entry);

            String testedModel = "gemini-3.5-flash-lite";
            String quotaTier = latency < 800 ? "Google AI Studio (Standard / Paid Tier)" : "Google AI Studio (Free Tier / Normal)";

            return TestKeyVaultResponse.builder()
                    .id(entry.getId())
                    .provider(entry.getProvider())
                    .status("ACTIVE")
                    .latencyMs(latency)
                    .testedModel(testedModel)
                    .quotaTier(quotaTier)
                    .message("Kết nối thành công tới máy chủ Google Gemini")
                    .testedAt(LocalDateTime.now())
                    .build();
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            log.error("Test khóa #{} thất bại: {}", id, ex.getMessage());
            entry.setStatus("DISABLED");
            vaultRepository.save(entry);

            return TestKeyVaultResponse.builder()
                    .id(entry.getId())
                    .provider(entry.getProvider())
                    .status("DISABLED")
                    .latencyMs(latency)
                    .testedModel("gemini-3.5-flash-lite")
                    .quotaTier("Không xác định")
                    .message("Kiểm tra thất bại: " + ex.getMessage())
                    .testedAt(LocalDateTime.now())
                    .build();
        }
    }

    @Transactional
    public AiKeyVaultDto updateKey(Long id, org.example.aiservice.dto.request.UpdateKeyVaultRequest request, String adminUsername) {
        AiKeyVaultEntry entry = vaultRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa với ID: " + id));

        if (request.getKeyName() != null && !request.getKeyName().isBlank()) {
            entry.setKeyName(request.getKeyName().trim());
        }
        if (request.getPriority() != null) {
            entry.setPriority(request.getPriority());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String newStatus = request.getStatus().toUpperCase();
            if (List.of("ACTIVE", "DISABLED").contains(newStatus)) {
                entry.setStatus(newStatus);
                if ("ACTIVE".equals(newStatus)) {
                    entry.setCooldownUntil(null);
                    entry.setFailureCount(0);
                }
            }
        }
        entry.setUpdatedBy(adminUsername);
        entry = vaultRepository.save(entry);
        return toDto(entry, LocalDateTime.now());
    }

    @Transactional
    public void deleteKey(Long id) {
        AiKeyVaultEntry entry = vaultRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa với ID: " + id));

        long activeCount = vaultRepository.countActiveKeysByProvider(entry.getProvider());
        if (activeCount <= 1 && "ACTIVE".equalsIgnoreCase(entry.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xóa khóa khả dụng duy nhất của nhà cung cấp " + entry.getProvider() + "!");
        }

        entry.setIsDeleted(true);
        vaultRepository.save(entry);
    }

    private AiKeyVaultDto toDto(AiKeyVaultEntry entry, LocalDateTime now) {
        long cooldownSecs = 0;
        if ("COOLDOWN".equalsIgnoreCase(entry.getStatus()) && entry.getCooldownUntil() != null) {
            if (entry.getCooldownUntil().isAfter(now)) {
                cooldownSecs = Duration.between(now, entry.getCooldownUntil()).toSeconds();
            }
        }

        String maskedKey = "••••••••" + (entry.getApiKeyLast4() != null ? entry.getApiKeyLast4() : "");

        return AiKeyVaultDto.builder()
                .id(entry.getId())
                .keyName(entry.getKeyName())
                .provider(entry.getProvider())
                .maskedKey(maskedKey)
                .priority(entry.getPriority())
                .status(entry.getStatus())
                .cooldownSecondsRemaining(cooldownSecs)
                .failureCount(entry.getFailureCount())
                .lastUsedAt(entry.getLastUsedAt())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
