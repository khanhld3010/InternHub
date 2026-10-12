package org.example.internservice.allowance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowanceAuditLogResponse {

    private Long id;
    private Long allowanceRecordId;

    private Integer previousWorkingDays;
    private Integer previousPaidLeaveDays;
    private BigDecimal previousBonusAmount;
    private BigDecimal previousDeductionAmount;
    private BigDecimal previousAmount;

    private Integer newWorkingDays;
    private Integer newPaidLeaveDays;
    private BigDecimal newBonusAmount;
    private BigDecimal newDeductionAmount;
    private BigDecimal newAmount;

    private String adjustmentReason;
    private Long adjustedById;
    private String adjustedByName;
    private LocalDateTime adjustedAt;
}
