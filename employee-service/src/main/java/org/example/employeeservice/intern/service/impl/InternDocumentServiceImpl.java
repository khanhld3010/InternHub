package org.example.employeeservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.storage.FileStorageService;
import org.example.employeeservice.exception.BadRequestException;
import org.example.employeeservice.exception.ResourceNotFoundException;
import org.example.employeeservice.intern.dto.response.DocumentResponse;
import org.example.employeeservice.intern.entity.InternDocument;
import org.example.employeeservice.intern.entity.InternProfile;
import org.example.employeeservice.intern.entity.enums.DocumentStatus;
import org.example.employeeservice.intern.entity.enums.DocumentType;
import org.example.employeeservice.intern.repository.InternDocumentRepository;
import org.example.employeeservice.intern.repository.InternProfileRepository;
import org.example.employeeservice.intern.service.InternDocumentService;
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

    @Override
    @Transactional
    public DocumentResponse uploadDocument(String internCode, MultipartFile file, String documentTypeStr) {
        log.info("Bắt đầu xử lý tải lên tài liệu cho internCode: {}, documentType: {}", internCode, documentTypeStr);

        // 1. Xác thực tính hợp lệ của tệp tin tải lên (Extract Method)
        validateFile(file);

        // 2. Xác thực loại tài liệu
        DocumentType documentType = parseDocumentType(documentTypeStr);

        // 3. Kiểm tra hồ sơ thực tập sinh tồn tại và trạng thái có cho phép nộp tài liệu không
        InternProfile internProfile = getAndValidateInternProfile(internCode);

        // 4. Lưu file vật lý trên đĩa
        String subDirectory = "interns/" + internProfile.getInternCode();
        String uniqueFileName = fileStorageService.storeFile(file, subDirectory);
        String relativeFilePath = subDirectory + "/" + uniqueFileName;

        // 5. Tạo Entity và lưu Metadata vào Database (với cơ chế rollback dọn dẹp file nếu DB lỗi)
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

        // 6. Chuyển đổi và trả về DTO
        return mapToDocumentResponse(savedDocument, internProfile.getInternCode());
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
            throw new BadRequestException("Loại tài liệu không được để trống. Chỉ chấp nhận: CV, APPLICATION_LETTER");
        }
        try {
            return DocumentType.valueOf(documentTypeStr.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BadRequestException("Loại tài liệu không hợp lệ. Chỉ chấp nhận: CV, APPLICATION_LETTER");
        }
    }

    private InternProfile getAndValidateInternProfile(String internCode) {
        InternProfile internProfile = internProfileRepository.findByInternCode(internCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với mã: " + internCode));

        // Ràng buộc nghiệp vụ: Không cho phép nộp tài liệu vào hồ sơ đã đóng (COMPLETED hoặc REJECTED)
        if (internProfile.getStatus() == org.example.employeeservice.intern.entity.enums.InternStatus.COMPLETED
                || internProfile.getStatus() == org.example.employeeservice.intern.entity.enums.InternStatus.REJECTED) {
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
                .createdAt(savedDocument.getCreatedAt())
                .build();
    }
}
