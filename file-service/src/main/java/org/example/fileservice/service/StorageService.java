package org.example.fileservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.fileservice.config.S3StorageConfig.S3Properties;
import org.example.fileservice.dto.StorageDtos.*;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties props;

    private static final long DEFAULT_UPLOAD_EXPIRE_SECONDS = 900; // 15 phút
    private static final int DEFAULT_VIEW_EXPIRE_MINUTES = 30;     // 30 phút

    @PostConstruct
    public void initBucket() {
        String bucket = props.getBucketName();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 Bucket '{}' already exists and is accessible.", bucket);
        } catch (NoSuchBucketException e) {
            log.warn("S3 Bucket '{}' does not exist. Auto-creating...", bucket);
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("S3 Bucket '{}' created successfully (Self-Healing).", bucket);
            } catch (Exception createEx) {
                log.error("Failed to auto-create S3 Bucket '{}': {}", bucket, createEx.getMessage());
            }
        } catch (Exception e) {
            log.warn("Could not verify S3 Bucket '{}' during startup (will attempt create): {}", bucket, e.getMessage());
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("S3 Bucket '{}' created successfully.", bucket);
            } catch (Exception createEx) {
                log.debug("Bucket '{}' may already exist or cannot be created: {}", bucket, createEx.getMessage());
            }
        }
    }

    public PresignedUploadResponse createPresignedUpload(PresignedUploadRequest request) {
        String prefix = (request.getPrefix() != null && !request.getPrefix().isBlank()) 
                ? request.getPrefix() : "temp";
        
        String extension = "";
        if (request.getFileName() != null && request.getFileName().contains(".")) {
            extension = request.getFileName().substring(request.getFileName().lastIndexOf("."));
        }
        
        String tempKey = String.format("%s/%s%s", prefix, UUID.randomUUID(), extension);
        String contentType = request.getContentType() != null ? request.getContentType() : "application/octet-stream";

        PutObjectRequest.Builder objectRequestBuilder = PutObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(tempKey)
                .contentType(contentType);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(DEFAULT_UPLOAD_EXPIRE_SECONDS))
                .putObjectRequest(objectRequestBuilder.build())
                .build();

        String url = s3Presigner.presignPutObject(presignRequest).url().toString();
        log.info("Generated presigned PUT URL for key: {}", tempKey);

        return PresignedUploadResponse.builder()
                .tempKey(tempKey)
                .presignedUrl(url)
                .expiresInSeconds(DEFAULT_UPLOAD_EXPIRE_SECONDS)
                .build();
    }

    public PromoteFileResponse promoteFile(PromoteFileRequest request) {
        String bucket = props.getBucketName();
        String sourceKey = request.getTempKey();
        String destKey = request.getDestinationKey();

        log.info("Promoting file from {} to {}", sourceKey, destKey);

        // 1. Copy Object từ temp sang destination
        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(destKey)
                .build();

        CopyObjectResponse copyResponse = s3Client.copyObject(copyRequest);

        // 2. Lấy metadata của file đích
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(destKey)
                .build();
        HeadObjectResponse headResponse = s3Client.headObject(headRequest);

        // 3. Xóa file nguồn trong thư mục temp
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(sourceKey).build());
        } catch (Exception e) {
            log.warn("Failed to delete temp object {}: {}", sourceKey, e.getMessage());
        }

        return PromoteFileResponse.builder()
                .finalKey(destKey)
                .fileSize(headResponse.contentLength())
                .contentType(headResponse.contentType())
                .etag(copyResponse.copyObjectResult().eTag())
                .build();
    }

    public PresignedViewResponse createPresignedView(PresignedViewRequest request) {
        int minutes = (request.getExpiresInMinutes() != null && request.getExpiresInMinutes() > 0)
                ? request.getExpiresInMinutes() : DEFAULT_VIEW_EXPIRE_MINUTES;

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(request.getFileKey())
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(minutes))
                .getObjectRequest(getObjectRequest)
                .build();

        String url = s3Presigner.presignGetObject(presignRequest).url().toString();
        log.info("Generated presigned GET URL for key: {}", request.getFileKey());

        return PresignedViewResponse.builder()
                .presignedUrl(url)
                .expiresInSeconds((long) minutes * 60)
                .build();
    }
}
