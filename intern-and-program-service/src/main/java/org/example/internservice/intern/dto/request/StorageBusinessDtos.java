package org.example.internservice.intern.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class StorageBusinessDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestUploadUrlRequest {
        @NotBlank(message = "Tên tệp không được để trống")
        private String fileName;

        @NotBlank(message = "Content-Type không được để trống")
        private String contentType;

        @NotBlank(message = "Loại tài liệu không được để trống")
        private String documentType; // CV, INTERNSHIP_APPLICATION, etc.

        private Long fileSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestUploadUrlResponse {
        private String tempKey;
        private String presignedUrl;
        private long expiresInSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmUploadRequest {
        @NotBlank(message = "tempKey không được để trống")
        private String tempKey;

        @NotBlank(message = "Tên tệp gốc không được để trống")
        private String originalFileName;

        @NotBlank(message = "Loại tài liệu không được để trống")
        private String documentType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViewDocumentUrlResponse {
        private Long documentId;
        private String fileName;
        private String presignedUrl;
        private long expiresInSeconds;
    }
}
