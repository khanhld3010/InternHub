package org.example.internservice.contract.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "contract_revisions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contract_revision", columnNames = {"contract_id", "revision_number"})
        },
        indexes = {
                @Index(name = "idx_revision_contract", columnList = "contract_id"),
                @Index(name = "idx_revision_status", columnList = "status"),
                @Index(name = "idx_revision_template_ver", columnList = "template_version_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractRevision extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_version_id", nullable = false)
    private ContractTemplateVersion templateVersion;

    @Column(name = "variables_payload", columnDefinition = "JSON")
    private String variablesPayload;

    @Column(name = "canonical_snapshot_content", columnDefinition = "LONGTEXT")
    private String canonicalSnapshotContent;

    @Column(name = "snapshot_hash", length = 64)
    private String snapshotHash;

    @Column(name = "pdf_storage_key", length = 255)
    private String pdfStorageKey;

    @Column(name = "pdf_hash", length = 64)
    private String pdfHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DynamicContractStatus status = DynamicContractStatus.DRAFT;

    @Column(name = "change_request_reason", columnDefinition = "TEXT")
    private String changeRequestReason;

    @Column(name = "consent_text_version", length = 50)
    private String consentTextVersion;

    @Column(name = "consent_text_snapshot", columnDefinition = "TEXT")
    private String consentTextSnapshot;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToOne(mappedBy = "contractRevision", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ContractSignature signature;
}
