package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.intern.entity.enums.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractResponse {

    private Long id;
    private String internCode;
    private String internFullName;
    private String contractNumber;
    private String contractTitle;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal allowanceAmount;
    private ContractStatus status;
    private String originalFileName;
    private Long fileSize;
    private String contentType;
    private String uploadedBy;
    private LocalDateTime signedAt;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
