package org.example.internservice.contract.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.contract.entity.enums.TemplateVersionStatus;

@Entity
@Table(
        name = "contract_template_versions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_template_version", columnNames = {"template_id", "version_number"})
        },
        indexes = {
                @Index(name = "idx_template_ver_template", columnList = "template_id"),
                @Index(name = "idx_template_ver_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractTemplateVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ContractTemplate contractTemplate;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "content_template", nullable = false, columnDefinition = "LONGTEXT")
    private String contentTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TemplateVersionStatus status = TemplateVersionStatus.ACTIVE;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
}
