package org.example.internservice.allowance.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.allowance.entity.enums.AllowanceCalculationType;
import org.example.internservice.allowance.entity.enums.AllowanceStatus;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.contract.entity.Contract;
import org.example.internservice.intern.entity.InternProfile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "intern_allowance_records",
        indexes = {
                @Index(name = "idx_allowance_intern_user", columnList = "intern_user_id"),
                @Index(name = "idx_allowance_period", columnList = "period_month"),
                @Index(name = "idx_allowance_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_intern_period_contract", columnNames = {"intern_user_id", "period_month", "contract_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternAllowanceRecord extends BaseEntity {

    @Column(name = "intern_user_id", nullable = false)
    private Long internUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_profile_id", nullable = false)
    private InternProfile internProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth; // YYYY-MM

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 20)
    @Builder.Default
    private AllowanceCalculationType calculationType = AllowanceCalculationType.MONTHLY;

    @Column(name = "base_allowance", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseAllowance;

    @Column(name = "standard_working_days", nullable = false)
    private Integer standardWorkingDays;

    @Column(name = "auto_calculated_days", nullable = false)
    @Builder.Default
    private Integer autoCalculatedDays = 0;

    @Column(name = "auto_calculated_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal autoCalculatedAmount = BigDecimal.ZERO;

    @Column(name = "actual_working_days", nullable = false)
    @Builder.Default
    private Integer actualWorkingDays = 0;

    @Column(name = "paid_leave_days", nullable = false)
    @Builder.Default
    private Integer paidLeaveDays = 0;

    @Column(name = "bonus_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal bonusAmount = BigDecimal.ZERO;

    @Column(name = "deduction_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "retro_adjustment_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal retroAdjustmentAmount = BigDecimal.ZERO;

    @Column(name = "retro_ref_period_month", length = 7)
    private String retroRefPeriodMonth;

    @Column(name = "retro_reason", columnDefinition = "TEXT")
    private String retroReason;

    @Column(name = "actual_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal actualAmount = BigDecimal.ZERO;

    @Column(name = "is_manually_adjusted", nullable = false)
    @Builder.Default
    private Boolean isManuallyAdjusted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AllowanceStatus status = AllowanceStatus.DRAFT;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @OneToMany(mappedBy = "allowanceRecord", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("adjustedAt DESC")
    @Builder.Default
    private List<InternAllowanceAuditLog> auditLogs = new ArrayList<>();

    public boolean isLocked() {
        return this.status != AllowanceStatus.DRAFT;
    }
}
