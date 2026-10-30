package org.example.internservice.intern.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.exception.ForbiddenException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.FileServiceClient;
import org.example.internservice.intern.client.FileServiceClient.PresignedUploadRequest;
import org.example.internservice.intern.client.FileServiceClient.PresignedUploadResponse;
import org.example.internservice.intern.client.FileServiceClient.PromoteFileRequest;
import org.example.internservice.intern.client.FileServiceClient.PromoteFileResponse;
import org.example.internservice.intern.client.FileServiceClient.PresignedViewRequest;
import org.example.internservice.intern.client.FileServiceClient.PresignedViewResponse;
import org.example.internservice.intern.dto.request.StorageBusinessDtos.ConfirmUploadRequest;
import org.example.internservice.intern.dto.request.StorageBusinessDtos.RequestUploadUrlRequest;
import org.example.internservice.intern.dto.request.StorageBusinessDtos.RequestUploadUrlResponse;
import org.example.internservice.intern.dto.request.StorageBusinessDtos.ViewDocumentUrlResponse;
import org.example.internservice.intern.dto.response.DocumentResponse;
import org.example.internservice.intern.entity.InternDocument;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.DocumentStatus;
import org.example.internservice.intern.entity.enums.DocumentType;
import org.example.internservice.intern.repository.InternDocumentRepository;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Helper chuyên biệt cho các nghiệp vụ Presigned Upload / S3 Cloud Storage của Intern Document.
 * Tuân thủ CONSTRAINTS.md: Giữ class gọn nhẹ < 150 dòng, tách rời khỏi Service chính để chống God-Class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternDocumentStorageHelper {

    private final FileServiceClient fileServiceClient;
    private final InternDocumentRepository internDocumentRepository;
    private final MentorAccessHelper mentorAccessHelper;

    public RequestUploadUrlResponse createPresignedUploadUrl(String internCode, RequestUploadUrlRequest request) {
        log.info("Cấp link upload trực tiếp S3 cho intern: {}, fileName: {}", internCode, request.getFileName());

        PresignedUploadRequest internalReq = PresignedUploadRequest.builder()
                .prefix("temp")
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .sizeLimitBytes(request.getFileSize())
                .build();

        PresignedUploadResponse internalRes = fileServiceClient.createPresignedUpload(internalReq);

        return RequestUploadUrlResponse.builder()
                .tempKey(internalRes.getTempKey())
                .presignedUrl(internalRes.getPresignedUrl())
                .expiresInSeconds(internalRes.getExpiresInSeconds())
                .build();
    }

    public DocumentResponse confirmUpload(
            InternProfile internProfile,
            DocumentType documentType,
            ConfirmUploadRequest request) {
        String internCode = internProfile.getInternCode();
        log.info("Xác nhận upload thành công từ client cho intern: {}, tempKey: {}", internCode, request.getTempKey());

        String extension = "";
        if (request.getOriginalFileName().contains(".")) {
            extension = request.getOriginalFileName().substring(request.getOriginalFileName().lastIndexOf("."));
        }
        String destKey = String.format("documents/%s/%s_%d_%s%s",
                internCode, documentType.name(), System.currentTimeMillis(), UUID.randomUUID().toString().substring(0, 8), extension);

        PromoteFileRequest promoteReq = PromoteFileRequest.builder()
                .tempKey(request.getTempKey())
                .destinationKey(destKey)
                .build();

        PromoteFileResponse promoteRes = fileServiceClient.promoteFile(promoteReq);

        InternDocument document = InternDocument.builder()
                .internProfile(internProfile)
                .documentType(documentType)
                .originalFileName(request.getOriginalFileName())
                .fileName(destKey)
                .filePath(promoteRes.getFinalKey())
                .fileSize(promoteRes.getFileSize() != null ? promoteRes.getFileSize() : 0L)
                .contentType(promoteRes.getContentType() != null ? promoteRes.getContentType() : "application/octet-stream")
                .status(DocumentStatus.PENDING_REVIEW)
                .build();

        InternDocument saved = internDocumentRepository.save(document);
        log.info("Ghi nhận tài liệu thành công vào DB với ID: {}", saved.getId());
        return mapToDocumentResponse(saved, internCode);
    }

    public ViewDocumentUrlResponse getDocumentViewUrl(Long documentId, CustomUserDetails userDetails) {
        InternDocument document = internDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu với ID: " + documentId));

        validateDocumentAccess(document.getInternProfile(), userDetails);

        String filePath = document.getFilePath();
        if (filePath != null && (filePath.startsWith("documents/") || filePath.startsWith("temp/"))) {
            PresignedViewRequest viewReq = PresignedViewRequest.builder()
                    .fileKey(filePath)
                    .expiresInMinutes(30)
                    .build();

            PresignedViewResponse viewRes = fileServiceClient.createPresignedView(viewReq);

            return ViewDocumentUrlResponse.builder()
                    .documentId(document.getId())
                    .fileName(document.getOriginalFileName())
                    .presignedUrl(viewRes.getPresignedUrl())
                    .expiresInSeconds(viewRes.getExpiresInSeconds())
                    .build();
        }

        return ViewDocumentUrlResponse.builder()
                .documentId(document.getId())
                .fileName(document.getOriginalFileName())
                .presignedUrl(null)
                .expiresInSeconds(0)
                .build();
    }

    public void validateDocumentAccess(InternProfile targetProfile, CustomUserDetails userDetails) {
        if (userDetails == null || targetProfile == null) {
            return;
        }

        boolean isHrOrAdmin = userDetails.getAuthorities() != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR")
                        || a.getAuthority().equals("ROLE_HR_MANAGER")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("DOCUMENT_REVIEW"));
        if (isHrOrAdmin) {
            return;
        }

        Long requestingUserId = userDetails.getUserId();
        if (requestingUserId != null && requestingUserId.equals(targetProfile.getUserId())) {
            return;
        }

        boolean isMentor = userDetails.getAuthorities() != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MENTOR"));
        if (isMentor && mentorAccessHelper != null && mentorAccessHelper.isInternManagedByMentor(targetProfile, userDetails)) {
            return;
        }

        log.warn("Cảnh báo IDOR: User id={}, role={} cố gắng truy cập tài liệu của internCode={}, userId={}",
                requestingUserId, userDetails.getRole(), targetProfile.getInternCode(), targetProfile.getUserId());
        throw new ForbiddenException("Bạn không có quyền xem hoặc tải tài liệu của thực tập sinh này");
    }

    private DocumentResponse mapToDocumentResponse(InternDocument savedDocument, String internCode) {
        return DocumentResponse.builder()
                .id(savedDocument.getId())
                .internCode(internCode)
                .documentType(savedDocument.getDocumentType())
                .originalFileName(savedDocument.getOriginalFileName())
                .fileSize(savedDocument.getFileSize())
                .contentType(savedDocument.getContentType())
                .status(savedDocument.getStatus())
                .rejectionReason(savedDocument.getRejectionReason())
                .createdAt(savedDocument.getCreatedAt())
                .updatedAt(savedDocument.getUpdatedAt())
                .build();
    }
}
