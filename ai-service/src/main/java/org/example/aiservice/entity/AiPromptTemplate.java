package org.example.aiservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_prompt_templates",
       uniqueConstraints = @UniqueConstraint(name = "uk_template_code_version", columnNames = {"template_code", "version"}),
       indexes = {
               @Index(name = "idx_template_code_active", columnList = "template_code, is_active"),
               @Index(name = "idx_feature_group", columnList = "feature_group")
       }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    @Column(name = "feature_group", nullable = false, length = 50)
    private String featureGroup;

    @Column(name = "prompt_type", nullable = false, length = 30)
    @Builder.Default
    private String promptType = "USER";

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "template_content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String templateContent;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "available_variables_json", columnDefinition = "TEXT")
    private String availableVariablesJson;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
