package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.internservice.intern.entity.enums.ContractType;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ContractStorageDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestContractUploadUrlRequest {
        @NotBlank(message = "Tên tệp không được để trống")
        private String fileName;

        @NotBlank(message = "Content-Type không được để trống")
        private String contentType;

        private Long fileSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmContractUploadRequest {
        @NotBlank(message = "tempKey không được để trống")
        private String tempKey;

        @NotBlank(message = "Tên tệp gốc không được để trống")
        private String originalFileName;

        @NotBlank(message = "Tiêu đề hợp đồng không được để trống")
        @Size(min = 3, max = 200, message = "Tiêu đề hợp đồng phải từ 3 đến 200 ký tự")
        private String contractTitle;

        @NotNull(message = "Ngày bắt đầu hợp đồng không được để trống")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;

        @NotNull(message = "Ngày kết thúc hợp đồng không được để trống")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;

        @Size(max = 50, message = "Mã hợp đồng không được vượt quá 50 ký tự")
        private String contractNumber;

        @DecimalMin(value = "0.0", inclusive = true, message = "Mức phụ cấp không được âm")
        private BigDecimal allowanceAmount;

        private ContractType contractType;

        private Long parentContractId;

        @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViewContractUrlResponse {
        private Long contractId;
        private String contractNumber;
        private String originalFileName;
        private String presignedUrl;
        private long expiresInSeconds;
    }
}
