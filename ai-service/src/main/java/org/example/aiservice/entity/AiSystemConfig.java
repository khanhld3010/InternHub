package org.example.aiservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_system_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String provider = "GEMINI";

    @Column(name = "model_name", nullable = false, length = 100)
    @Builder.Default
    private String modelName = "gemini-1.5-flash";

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "api_key_last4", length = 4)
    private String apiKeyLast4;

    @Column(name = "key_version", nullable = false)
    @Builder.Default
    private Integer keyVersion = 1;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal temperature = new BigDecimal("0.30");

    @Column(name = "max_tokens", nullable = false)
    @Builder.Default
    private Integer maxTokens = 1024;

    @Column(name = "system_instruction", columnDefinition = "TEXT")
    private String systemInstruction;

    @Column(name = "quick_prompts_json", columnDefinition = "TEXT")
    private String quickPromptsJson;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
