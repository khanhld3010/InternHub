package org.example.internservice.allowance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.allowance.entity.enums.AllowanceCalculationType;
import org.example.internservice.allowance.entity.enums.AllowanceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowanceRecordResponse {

    private Long id;
    private Long internUserId;
    private Long internProfileId;
    private String internFullName;
    private String internEmail;
    private Long contractId;
    private String contractNumber;
    private String contractStatus;
    private String periodMonth;

    private AllowanceCalculationType calculationType;
    private BigDecimal baseAllowance;
    private Integer standardWorkingDays;

    private Integer autoCalculatedDays;
    private BigDecimal autoCalculatedAmount;

    private Integer actualWorkingDays;
    private Integer paidLeaveDays;
    private BigDecimal bonusAmount;
    private BigDecimal deductionAmount;

    private BigDecimal retroAdjustmentAmount;
    private String retroRefPeriodMonth;
    private String retroReason;

    private BigDecimal actualAmount;

    private Boolean isManuallyAdjusted;
    private AllowanceStatus status;
    private Boolean isLocked;
    private LocalDate paymentDate;
    private String notes;

    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
