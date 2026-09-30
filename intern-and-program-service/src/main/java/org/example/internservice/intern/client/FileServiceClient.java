package org.example.internservice.intern.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class FileServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.file-service.url:http://file-service:8084}")
    private String fileServiceUrl;

    public FileServiceClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public PresignedUploadResponse createPresignedUpload(PresignedUploadRequest request) {
        String url = fileServiceUrl + "/internal/files/presigned-upload";
        try {
            ResponseEntity<PresignedUploadResponse> response = restTemplate.postForEntity(url, request, PresignedUploadResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to request presigned upload from file-service: {}", e.getMessage());
            throw new RuntimeException("Lỗi hệ thống khi sinh liên kết upload tài liệu", e);
        }
    }

    public PromoteFileResponse promoteFile(PromoteFileRequest request) {
        String url = fileServiceUrl + "/internal/files/promote";
        try {
            ResponseEntity<PromoteFileResponse> response = restTemplate.postForEntity(url, request, PromoteFileResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to promote file in file-service: {}", e.getMessage());
            throw new RuntimeException("Lỗi hệ thống khi lưu trữ vĩnh viễn tài liệu", e);
        }
    }

    public PresignedViewResponse createPresignedView(PresignedViewRequest request) {
        String url = fileServiceUrl + "/internal/files/presigned-view";
        try {
            ResponseEntity<PresignedViewResponse> response = restTemplate.postForEntity(url, request, PresignedViewResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to request presigned view from file-service: {}", e.getMessage());
            throw new RuntimeException("Lỗi hệ thống khi sinh liên kết xem tài liệu", e);
        }
    }

    // DTOs
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedUploadRequest {
        private String prefix;
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
