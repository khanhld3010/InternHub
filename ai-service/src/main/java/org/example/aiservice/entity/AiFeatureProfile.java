package org.example.aiservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_feature_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFeatureProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_key", nullable = false, unique = true, length = 50)
    private String featureKey;

    @Column(name = "feature_name", nullable = false, length = 100)
    private String featureName;

    @Column(length = 255)
    private String description;

    @Column(name = "model_name", nullable = false, length = 100)
    @Builder.Default
    private String modelName = "gemini-3.5-flash-lite";

    @Column(nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal temperature = new BigDecimal("0.20");

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

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
