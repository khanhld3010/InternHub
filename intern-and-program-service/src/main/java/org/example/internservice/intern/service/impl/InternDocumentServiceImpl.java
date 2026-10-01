package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.storage.FileStorageService;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.dto.request.ReviewDocumentRequest;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.intern.dto.response.DocumentResponse;
import org.example.internservice.intern.entity.InternDocument;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.DocumentStatus;
import org.example.internservice.intern.entity.enums.DocumentType;
import org.example.internservice.intern.repository.InternDocumentRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.service.InternDocumentService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternDocumentServiceImpl implements InternDocumentService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".pdf", ".docx", ".doc");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/octet-stream"
    );

    private final InternProfileRepository internProfileRepository;
    private final InternDocumentRepository internDocumentRepository;
    private final FileStorageService fileStorageService;
    private final org.example.internservice.intern.client.FileServiceClient fileServiceClient;
    private final org.example.internservice.intern.client.NotificationEventDispatcher notificationEventDispatcher;


    @Override
    @Transactional
    public DocumentResponse uploadDocument(String internCode, MultipartFile file, String documentTypeStr) {
        log.info("Bắt đầu xử lý tải lên tài liệu cho internCode: {}, documentType: {}", internCode, documentTypeStr);

        validateFile(file);
        DocumentType documentType = parseDocumentType(documentTypeStr);
        InternProfile internProfile = getAndValidateInternProfile(internCode);

        String subDirectory = "interns/" + internProfile.getInternCode();
        String uniqueFileName = fileStorageService.storeFile(file, subDirectory);
        String relativeFilePath = subDirectory + "/" + uniqueFileName;

        InternDocument document = InternDocument.builder()
                .internProfile(internProfile)
                .documentType(documentType)
                .originalFileName(Objects.requireNonNullElse(file.getOriginalFilename(), "file"))
                .fileName(uniqueFileName)
                .filePath(relativeFilePath)
                .fileSize(file.getSize())
                .contentType(StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream")
                .status(DocumentStatus.PENDING_REVIEW)
                .build();

        InternDocument savedDocument = saveDocumentMetadataWithRollback(document, relativeFilePath);

        return mapToDocumentResponse(savedDocument, internProfile.getInternCode());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByInternCode(String internCode) {
        log.info("Lấy danh sách tài liệu cho thực tập sinh: {}", internCode);
        if (!StringUtils.hasText(internCode)) {
            throw new BadRequestException("Mã thực tập sinh không được để trống");
        }

        InternProfile profile = internProfileRepository.findByInternCode(internCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với mã: " + internCode));

        List<InternDocument> documents = internDocumentRepository.findByInternProfileInternCodeOrderByCreatedAtDesc(profile.getInternCode());

        return documents.stream()
                .map(doc -> mapToDocumentResponse(doc, profile.getInternCode()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDownloadDto loadDocumentForDownload(Long documentId) {
        log.info("Tải tài liệu với documentId: {}", documentId);
        if (documentId == null || documentId <= 0) {
            throw new BadRequestException("ID tài liệu không hợp lệ");
        }

        InternDocument document = internDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu với ID: " + documentId));

        Resource resource = fileStorageService.loadFileAsResource(document.getFilePath());

        return DocumentDownloadDto.builder()
                .resource(resource)
                .originalFileName(document.getOriginalFileName())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .build();
    }

    @Override
    @Transactional
    public DocumentResponse reviewDocument(Long documentId, ReviewDocumentRequest request) {
        log.info("Xét duyệt tài liệu ID: {}, request: {}", documentId, request);
        if (documentId == null || documentId <= 0) {
            throw new BadRequestException("ID tài liệu không hợp lệ");
        }

        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("Trạng thái xét duyệt không được để trống");
        }

        InternDocument document = internDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu với ID: " + documentId));

        if (request.getStatus() == DocumentStatus.REJECTED) {
            String reason = request.getRejectionReason();
            if (!StringUtils.hasText(reason) || reason.trim().length() < 5) {
                log.warn("Từ chối tài liệu thất bại: Lý do từ chối không hợp lệ ({})", reason);
                throw new BadRequestException("Lý do từ chối không được để trống và phải có ít nhất 5 ký tự");
            }
            document.setStatus(DocumentStatus.REJECTED);
            document.setRejectionReason(reason.trim());
        } else if (request.getStatus() == DocumentStatus.APPROVED) {
            document.setStatus(DocumentStatus.APPROVED);
            document.setRejectionReason(null);
        } else {
            throw new BadRequestException("Trạng thái xét duyệt không hợp lệ. Chỉ chấp nhận: APPROVED, REJECTED");
        }

        InternDocument updated = internDocumentRepository.save(document);
        log.info("Cập nhật thành công trạng thái tài liệu ID: {} thành {}", updated.getId(), updated.getStatus());

        // Bắn thông báo real-time tới thực tập sinh
        notifyDocumentReviewResult(updated);

        return mapToDocumentResponse(updated, updated.getInternProfile().getInternCode());
    }

    private void notifyDocumentReviewResult(InternDocument doc) {
        if (doc == null || doc.getInternProfile() == null || doc.getInternProfile().getUserId() == null) {
            return;
        }
        Long internUserId = doc.getInternProfile().getUserId();
        String docTypeName = doc.getDocumentType() != null ? doc.getDocumentType().name() : "Tài liệu";
        boolean isApproved = doc.getStatus() == DocumentStatus.APPROVED;

        String title = isApproved ? "Tài liệu được duyệt" : "Tài liệu bị từ chối";
        String content = isApproved
                ? String.format("Tài liệu %s (%s) của bạn đã được phê duyệt.", docTypeName, doc.getOriginalFileName())
                : String.format("Tài liệu %s (%s) của bạn đã bị từ chối. Lý do: %s",
                        docTypeName, doc.getOriginalFileName(), doc.getRejectionReason());

        notificationEventDispatcher.dispatch(org.example.internservice.intern.client.dto.CreateNotificationInternalRequest.builder()
                .recipientId(internUserId)
                .title(title)
                .content(content)
                .type(isApproved ? "DOCUMENT_APPROVED" : "DOCUMENT_REJECTED")
                .referenceType("DOCUMENT")
                .referenceId(String.valueOf(doc.getId()))
                .actionUrl("/profile")
                .build());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tin tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Dung lượng tệp tin vượt quá giới hạn cho phép (tối đa 5MB)");
        }

        String rawOriginalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        String extension = StringUtils.getFilenameExtension(rawOriginalFilename);
        String fileExtension = StringUtils.hasText(extension) ? "." + extension.toLowerCase() : "";

        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new BadRequestException("Định dạng tệp tin không hợp lệ. Chỉ chấp nhận các định dạng: .pdf, .docx, .doc");
        }

        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("MIME type không thuộc danh sách whitelist: {}", contentType);
            throw new BadRequestException("Định dạng MIME của tệp tin không được hỗ trợ");
        }
    }

    private DocumentType parseDocumentType(String documentTypeStr) {
        if (!StringUtils.hasText(documentTypeStr)) {
            throw new BadRequestException("Loại tài liệu không được để trống");
        }
        try {
            return DocumentType.valueOf(documentTypeStr.trim().toUpperCase());
        } catch (Exception ex) {
            log.warn("Loại tài liệu không hợp lệ: {}", documentTypeStr);
            throw new BadRequestException("Loại tài liệu không hợp lệ. Chỉ chấp nhận: CV hoặc APPLICATION_LETTER");
        }
    }

    private InternProfile getAndValidateInternProfile(String internCode) {
        if (!StringUtils.hasText(internCode)) {
            throw new BadRequestException("Mã thực tập sinh không được để trống");
        }

        InternProfile internProfile = internProfileRepository.findByInternCode(internCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với mã: " + internCode));

        if (internProfile.getStatus() == org.example.internservice.intern.entity.enums.InternStatus.COMPLETED
                || internProfile.getStatus() == org.example.internservice.intern.entity.enums.InternStatus.REJECTED) {
            log.warn("Từ chối upload tài liệu vì hồ sơ {} đang ở trạng thái đóng: {}", internCode, internProfile.getStatus());
            throw new BadRequestException("Hồ sơ thực tập sinh đã đóng (" + internProfile.getStatus() + "), không thể nộp thêm tài liệu");
        }

        return internProfile;
    }

    private InternDocument saveDocumentMetadataWithRollback(InternDocument document, String relativeFilePath) {
        try {
            InternDocument saved = internDocumentRepository.save(document);
            log.info("Lưu thành công tài liệu ID: {} cho intern: {}", saved.getId(), document.getInternProfile().getInternCode());
            return saved;
        } catch (Exception ex) {
            log.error("Lỗi khi lưu metadata tài liệu vào DB, tiến hành rollback xóa file vật lý: {}", relativeFilePath, ex);
            fileStorageService.deleteFile(relativeFilePath);
            throw new RuntimeException("Lỗi lưu trữ thông tin tài liệu. Vui lòng thử lại sau.", ex);
        }
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

    @Override
    public org.example.internservice.intern.dto.request.StorageBusinessDtos.RequestUploadUrlResponse createPresignedUploadUrl(
            String internCode, org.example.internservice.intern.dto.request.StorageBusinessDtos.RequestUploadUrlRequest request) {
        log.info("Cấp link upload trực tiếp S3 cho intern: {}, fileName: {}", internCode, request.getFileName());
        getAndValidateInternProfile(internCode);

        org.example.internservice.intern.client.FileServiceClient.PresignedUploadRequest internalReq =
                org.example.internservice.intern.client.FileServiceClient.PresignedUploadRequest.builder()
                        .prefix("temp")
                        .fileName(request.getFileName())
                        .contentType(request.getContentType())
                        .sizeLimitBytes(request.getFileSize())
                        .build();

        org.example.internservice.intern.client.FileServiceClient.PresignedUploadResponse internalRes =
                fileServiceClient.createPresignedUpload(internalReq);

        return org.example.internservice.intern.dto.request.StorageBusinessDtos.RequestUploadUrlResponse.builder()
                .tempKey(internalRes.getTempKey())
                .presignedUrl(internalRes.getPresignedUrl())
                .expiresInSeconds(internalRes.getExpiresInSeconds())
                .build();
    }

    @Override
    @Transactional
    public DocumentResponse confirmUpload(
            String internCode, org.example.internservice.intern.dto.request.StorageBusinessDtos.ConfirmUploadRequest request) {
        log.info("Xác nhận upload thành công từ client cho intern: {}, tempKey: {}", internCode, request.getTempKey());
        InternProfile internProfile = getAndValidateInternProfile(internCode);
        DocumentType documentType = parseDocumentType(request.getDocumentType());

        // Định dạng đường dẫn chính thức: documents/{internCode}/{documentType}_{timestamp}_{uuid}.pdf
        String extension = "";
        if (request.getOriginalFileName().contains(".")) {
            extension = request.getOriginalFileName().substring(request.getOriginalFileName().lastIndexOf("."));
        }
        String destKey = String.format("documents/%s/%s_%d_%s%s",
                internCode, documentType.name(), System.currentTimeMillis(), java.util.UUID.randomUUID().toString().substring(0, 8), extension);

        // Gọi file-service promote từ temp sang permanent
        org.example.internservice.intern.client.FileServiceClient.PromoteFileRequest promoteReq =
                org.example.internservice.intern.client.FileServiceClient.PromoteFileRequest.builder()
                        .tempKey(request.getTempKey())
                        .destinationKey(destKey)
                        .build();

        org.example.internservice.intern.client.FileServiceClient.PromoteFileResponse promoteRes = fileServiceClient.promoteFile(promoteReq);

        // Ghi vào database sau khi promote thành công 100%
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

    @Override
    public org.example.internservice.intern.dto.request.StorageBusinessDtos.ViewDocumentUrlResponse getDocumentViewUrl(Long documentId) {
        InternDocument document = internDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu với ID: " + documentId));

        // Kiểm tra nếu là file lưu trữ trên S3 (có prefix documents/ hoặc temp/)
        String filePath = document.getFilePath();
        if (filePath != null && (filePath.startsWith("documents/") || filePath.startsWith("temp/"))) {
            org.example.internservice.intern.client.FileServiceClient.PresignedViewRequest viewReq =
                    org.example.internservice.intern.client.FileServiceClient.PresignedViewRequest.builder()
                            .fileKey(filePath)
                            .expiresInMinutes(30)
                            .build();

            org.example.internservice.intern.client.FileServiceClient.PresignedViewResponse viewRes = fileServiceClient.createPresignedView(viewReq);

            return org.example.internservice.intern.dto.request.StorageBusinessDtos.ViewDocumentUrlResponse.builder()
                    .documentId(document.getId())
                    .fileName(document.getOriginalFileName())
                    .presignedUrl(viewRes.getPresignedUrl())
                    .expiresInSeconds(viewRes.getExpiresInSeconds())
                    .build();
        }

        // Với file cũ lưu local, trả về null presignedUrl để client fallback tải an toàn qua controller
        return org.example.internservice.intern.dto.request.StorageBusinessDtos.ViewDocumentUrlResponse.builder()
                .documentId(document.getId())
                .fileName(document.getOriginalFileName())
                .presignedUrl(null)
                .expiresInSeconds(0)
                .build();
    }
}


