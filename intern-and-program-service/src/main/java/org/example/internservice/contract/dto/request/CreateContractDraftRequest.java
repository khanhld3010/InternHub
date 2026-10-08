package org.example.internservice.contract.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateContractDraftRequest {

    @NotNull(message = "internId không được để trống")
    private Long internId;

    @NotNull(message = "programId không được để trống")
    private Long programId;

    @NotNull(message = "templateId không được để trống")
    private Long templateId;

    private Integer templateVersionNumber;

    private String position;
    private String department;
    private String supervisorName;
    private BigDecimal allowanceAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String customTerms;
}
