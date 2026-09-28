package org.example.fileservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class StorageDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedUploadRequest {
        private String prefix; // Mặc định là "temp"
        private String fileName;
        private String contentType;
        private Long sizeLimitBytes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedUploadResponse {
        private String tempKey;
        private String presignedUrl;
        private long expiresInSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromoteFileRequest {
        private String tempKey;
        private String destinationKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromoteFileResponse {
        private String finalKey;
        private Long fileSize;
        private String contentType;
        private String etag;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedViewRequest {
        private String fileKey;
        private Integer expiresInMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedViewResponse {
        private String presignedUrl;
        private long expiresInSeconds;
    }
}
