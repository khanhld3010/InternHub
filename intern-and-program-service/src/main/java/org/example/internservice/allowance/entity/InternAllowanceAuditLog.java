package org.example.internservice.allowance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "intern_allowance_audit_logs", indexes = {
        @Index(name = "idx_audit_allowance", columnList = "allowance_record_id"),
        @Index(name = "idx_audit_adjuster", columnList = "adjusted_by_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternAllowanceAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allowance_record_id", nullable = false)
    private InternAllowanceRecord allowanceRecord;

    @Column(name = "previous_working_days", nullable = false)
    private Integer previousWorkingDays;

    @Column(name = "previous_paid_leave_days", nullable = false)
    private Integer previousPaidLeaveDays;

    @Column(name = "previous_bonus_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal previousBonusAmount;

    @Column(name = "previous_deduction_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal previousDeductionAmount;

    @Column(name = "previous_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal previousAmount;

    @Column(name = "new_working_days", nullable = false)
    private Integer newWorkingDays;

    @Column(name = "new_paid_leave_days", nullable = false)
    private Integer newPaidLeaveDays;

    @Column(name = "new_bonus_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal newBonusAmount;

    @Column(name = "new_deduction_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal newDeductionAmount;

    @Column(name = "new_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal newAmount;

    @Column(name = "adjustment_reason", nullable = false, columnDefinition = "TEXT")
    private String adjustmentReason;

    @Column(name = "adjusted_by_id", nullable = false)
    private Long adjustedById;

    @Column(name = "adjusted_by_name", nullable = false, length = 100)
    private String adjustedByName;

    @Column(name = "adjusted_at", nullable = false)
    private LocalDateTime adjustedAt;

    @PrePersist
    protected void onCreate() {
        if (this.adjustedAt == null) {
            this.adjustedAt = LocalDateTime.now();
        }
    }
}
