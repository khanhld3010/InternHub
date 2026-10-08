package org.example.internservice.contract.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_signatures", indexes = {
        @Index(name = "idx_sig_revision", columnList = "contract_revision_id", unique = true),
        @Index(name = "idx_sig_user", columnList = "signer_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractSignature extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_revision_id", nullable = false, unique = true)
    private ContractRevision contractRevision;

    @Column(name = "signer_user_id", nullable = false)
    private Long signerUserId;

    @Column(name = "signature_data", nullable = false, columnDefinition = "LONGTEXT")
    private String signatureData; // Vector/SVG/Base64 từ Canvas

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", nullable = false, length = 500)
    private String userAgent;

    @Column(name = "auth_method", nullable = false, length = 50)
    @Builder.Default
    private String authMethod = "JWT_SESSION";

    @Column(name = "document_hash", nullable = false, length = 64)
    private String documentHash;

    @Column(name = "signature_hash", nullable = false, length = 64)
    private String signatureHash;

    @Column(name = "consent_text_version", nullable = false, length = 50)
    private String consentTextVersion;

    @Column(name = "consent_text_snapshot", nullable = false, columnDefinition = "TEXT")
    private String consentTextSnapshot;
}
