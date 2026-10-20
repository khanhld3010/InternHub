package org.example.aiservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.aiservice.entity.AiKeyVaultEntry;
import org.example.aiservice.repository.AiKeyVaultRepository;
import org.example.aiservice.security.AesGcmEncryptionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiKeyVaultManager {

    private final AiKeyVaultRepository vaultRepository;
    private final AesGcmEncryptionUtil encryptionUtil;

    /**
     * Thu hồi một Key khả dụng cho nhà cung cấp theo thuật toán Weighted Round-Robin & Tự chữa lành.
     */
    @Transactional
    public AiKeyVaultEntry acquireActiveKey(String provider) {
        String prov = (provider == null || provider.isBlank()) ? "GEMINI" : provider.toUpperCase();
        List<AiKeyVaultEntry> candidateKeys = vaultRepository.findByProviderAndIsDeletedFalseOrderByPriorityAscLastUsedAtAsc(prov);

        if (candidateKeys.isEmpty()) {
            throw new IllegalStateException("Kho khoa API (Vault) rong! Vui long cau hinh it nhat 1 API Key trong quan tri.");
        }

        LocalDateTime now = LocalDateTime.now();
        AiKeyVaultEntry selectedKey = null;

        for (AiKeyVaultEntry key : candidateKeys) {
            // Kiểm tra trạng thái và cooldown (Cơ chế Half-Open: Cho 1 request thử nghiệm đi qua trước)
            if ("COOLDOWN".equalsIgnoreCase(key.getStatus())) {
                if (key.getCooldownUntil() != null && now.isAfter(key.getCooldownUntil())) {
                    log.info("Key #{} '{}' da het thoi gian Cooldown 60s -> Dua vao nấc HALF_OPEN thử nghiệm", key.getId(), key.getKeyName());
                    key.setStatus("HALF_OPEN");
                    key.setCooldownUntil(null);
                    key.setLastUsedAt(now);
                    vaultRepository.save(key);
                    selectedKey = key;
                    break;
                } else {
                    log.debug("Bo qua Key #{} '{}' dang trong thoi gian nghi Cooldown", key.getId(), key.getKeyName());
                    continue;
                }
            } else if ("ACTIVE".equalsIgnoreCase(key.getStatus())) {
                // Atomic Concurrency: Cập nhật lastUsedAt ngay lập tức để thread tiếp theo chọn key khác
                key.setLastUsedAt(now);
                vaultRepository.save(key);
                selectedKey = key;
                break;
            }
        }

        if (selectedKey == null) {
            // Tất cả Key đều đang Cooldown hoặc Disabled -> fallback chọn Key Cooldown có thời gian kết thúc sớm nhất
            log.warn("Toan bo cac Key cua nha cung cap {} deu dang Cooldown hoac Disabled! Dang tim Key co the phuc hoi som nhat...", prov);
            selectedKey = candidateKeys.stream()
                    .filter(k -> !"DISABLED".equalsIgnoreCase(k.getStatus()))
                    .min((k1, k2) -> {
                        LocalDateTime t1 = k1.getCooldownUntil() != null ? k1.getCooldownUntil() : LocalDateTime.MAX;
                        LocalDateTime t2 = k2.getCooldownUntil() != null ? k2.getCooldownUntil() : LocalDateTime.MAX;
                        return t1.compareTo(t2);
                    })
                    .orElseThrow(() -> new IllegalStateException("Khong co bat ky API Key nao kha dung! Toan bo da bi vo hieu hoa."));
            selectedKey.setLastUsedAt(now);
            vaultRepository.save(selectedKey);
        }

        return selectedKey;
    }

    /**
     * Giải mã an toàn API Key thô từ bản ghi Key Vault.
     */
    public String resolveRawApiKey(AiKeyVaultEntry keyEntry) {
        if (keyEntry == null || keyEntry.getApiKeyEncrypted() == null) {
            throw new IllegalArgumentException("Ban ghi Key khong hop le!");
        }
        return encryptionUtil.decrypt(keyEntry.getApiKeyEncrypted(), 1);
    }

    /**
     * Báo cáo kết quả thực thi của một Key để cập nhật trạng thái Failover / Cooldown / Round-Robin.
     */
    @Transactional
    public void reportExecutionResult(Long keyId, int httpStatusCode) {
        if (keyId == null) return;

        vaultRepository.findById(keyId).ifPresent(key -> {
            LocalDateTime now = LocalDateTime.now();

            if (httpStatusCode == 429) {
                log.warn("API Key #{} '{}' bi loi HTTP 429 Quota Exceeded -> Dua vao Cooldown 60 giay", key.getId(), key.getKeyName());
                key.setStatus("COOLDOWN");
                key.setCooldownUntil(now.plusSeconds(60));
                key.setFailureCount(key.getFailureCount() + 1);
            } else if (httpStatusCode == 401 || httpStatusCode == 403) {
                log.error("API Key #{} '{}' bi loi HTTP {} (Sai key hoac bi thu hoi) -> VO HIEU HOA (DISABLED)", key.getId(), key.getKeyName(), httpStatusCode);
                key.setStatus("DISABLED");
                key.setFailureCount(key.getFailureCount() + 1);
            } else if (httpStatusCode >= 200 && httpStatusCode < 300) {
                if ("HALF_OPEN".equalsIgnoreCase(key.getStatus())) {
                    log.info("API Key #{} '{}' goi thu nghiem thanh cong -> Chinh thuc mo lai ACTIVE hoan toan", key.getId(), key.getKeyName());
                }
                key.setStatus("ACTIVE");
                key.setCooldownUntil(null);
                key.setLastUsedAt(now);
                key.setFailureCount(0);
            }
            vaultRepository.save(key);
        });
    }
}
