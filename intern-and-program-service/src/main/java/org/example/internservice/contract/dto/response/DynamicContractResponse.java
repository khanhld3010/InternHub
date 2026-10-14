package org.example.internservice.contract.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamicContractResponse {
    private Long id;
    private String contractNumber;
    private Long internId;
    private String internCode;
    private String internFullName;
    private String internEmail;
    private Long programId;
    private String programName;
    private DynamicContractStatus status;
    private Long currentRevisionId;
    private Integer currentRevisionNumber;
    private String snapshotHash;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private BigDecimal allowanceAmount;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
