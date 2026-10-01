package org.example.internservice.intern.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.intern.entity.enums.ContractStatus;
import org.example.internservice.intern.entity.enums.ContractType;
import org.example.internservice.intern.entity.enums.InternStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractResponse {

    private Long id;
    private Long parentContractId;
    private String parentContractNumber;
    private ContractType contractType;
    private String internCode;
    private String internFullName;
    private String internEmail;
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
    private String signerFullName;
    private String internConfirmationNote;
    private String rejectionReason;
    private String feedbackNotes;
    private LocalDateTime feedbackAt;
    private String terminationReason;
    private LocalDateTime terminatedAt;
    private String terminatedBy;
    private String notes;
    private InternStatus internProfileStatus;
    private Long daysRemaining;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
