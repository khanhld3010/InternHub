package org.example.aiservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_key_vault")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiKeyVaultEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_name", nullable = false, length = 100)
    private String keyName;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String provider = "GEMINI";

    @Column(name = "api_key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "api_key_last4", nullable = false, length = 4)
    private String apiKeyLast4;

    @Column(name = "api_key_hash", length = 64)
    private String apiKeyHash;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 1;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "cooldown_until")
    private LocalDateTime cooldownUntil;

    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    private Integer failureCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isCurrentlyUsable(LocalDateTime now) {
        if (Boolean.TRUE.equals(isDeleted)) {
            return false;
        }
        if ("DISABLED".equalsIgnoreCase(status)) {
            return false;
        }
        if ("COOLDOWN".equalsIgnoreCase(status)) {
            return cooldownUntil != null && now.isAfter(cooldownUntil);
        }
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
