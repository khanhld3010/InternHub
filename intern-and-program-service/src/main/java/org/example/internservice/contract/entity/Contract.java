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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.program.entity.InternshipProgram;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contracts", indexes = {
        @Index(name = "idx_contracts_number", columnList = "contract_number", unique = true),
        @Index(name = "idx_contracts_intern", columnList = "intern_id"),
        @Index(name = "idx_contracts_program", columnList = "program_id"),
        @Index(name = "idx_contracts_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract extends BaseEntity {

    @Column(name = "contract_number", nullable = false, unique = true, length = 100)
    private String contractNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternProfile internProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private InternshipProgram internshipProgram;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DynamicContractStatus status = DynamicContractStatus.DRAFT;

    @Column(name = "current_revision_id")
    private Long currentRevisionId;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "allowance_amount", precision = 15, scale = 2)
    private BigDecimal allowanceAmount;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("revisionNumber DESC")
    @Builder.Default
    private List<ContractRevision> revisions = new ArrayList<>();
}
