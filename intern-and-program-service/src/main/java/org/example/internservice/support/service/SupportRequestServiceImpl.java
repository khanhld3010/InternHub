package org.example.internservice.support.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.allowance.service.WorkingCalendarHelper;
import org.example.internservice.contract.entity.Contract;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;
import org.example.internservice.contract.repository.DynamicContractRepository;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ForbiddenException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.support.dto.request.CreateSupportRequestDto;
import org.example.internservice.support.dto.request.RespondSupportRequestDto;
import org.example.internservice.support.dto.response.SupportRequestResponse;
import org.example.internservice.support.entity.DocumentAccessLog;
import org.example.internservice.support.entity.SupportRequest;
import org.example.internservice.support.entity.SupportRequestStatusLog;
import org.example.internservice.support.entity.enums.SupportRequestStatus;
import org.example.internservice.support.entity.enums.SupportRequestType;
import org.example.internservice.support.repository.DocumentAccessLogRepository;
import org.example.internservice.support.repository.SupportRequestRepository;
import org.example.internservice.support.repository.SupportRequestStatusLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportRequestServiceImpl implements SupportRequestService {

    private final SupportRequestRepository supportRequestRepository;
    private final SupportRequestStatusLogRepository statusLogRepository;
    private final DocumentAccessLogRepository documentAccessLogRepository;
    private final InternProfileRepository internProfileRepository;
    private final DynamicContractRepository contractRepository;

    @Value("${app.storage.support-docs-dir:uploads/support-documents}")
    private String supportDocsDir;

    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "jpg", "jpeg", "png");
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    @Override
    @Transactional
    public SupportRequestResponse createRequest(CreateSupportRequestDto dto, MultipartFile attachment, Long internUserId) {
        InternProfile intern = internProfileRepository.findByUserId(internUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh cho User ID: " + internUserId));

        // 1. Chống spam: Mỗi TTS chỉ được có tối đa 1 yêu cầu PENDING hoặc PROCESSING cho cùng 1 loại
        if (supportRequestRepository.existsActiveRequestByType(internUserId, dto.getRequestType())) {
            throw new BadRequestException("Bạn đang có một yêu cầu loại " + dto.getRequestType()
                    + " đang trong quá trình xử lý. Vui lòng chờ phản hồi trước khi gửi tiếp.");
        }

        // 2. Validate điều kiện trạng thái theo loại giấy tờ
        validateInternEligibilityForRequest(intern, dto.getRequestType());

        // 3. Xử lý file đính kèm nếu có
        String storageKey = null;
        String originalFilename = null;
        if (attachment != null && !attachment.isEmpty()) {
            validateUploadedFile(attachment);
            storageKey = saveFileToInternalStorage(attachment, "requests");
            originalFilename = attachment.getOriginalFilename();
        }

        // 4. Tính SLA (+3 ngày làm việc)
        LocalDateTime slaDueDate = calculateSlaDueDate(LocalDateTime.now(), 3);

        SupportRequest request = SupportRequest.builder()
                .internUserId(internUserId)
                .internProfile(intern)
                .requestType(dto.getRequestType())
                .title(dto.getTitle())
                .content(dto.getContent())
                .attachmentStorageKey(storageKey)
                .attachmentFilename(originalFilename)
                .status(SupportRequestStatus.PENDING)
                .slaDueDate(slaDueDate)
                .build();

        SupportRequest saved = supportRequestRepository.save(request);

        // Lưu status log ban đầu
        statusLogRepository.save(SupportRequestStatusLog.builder()
                .supportRequest(saved)
                .fromStatus(SupportRequestStatus.PENDING)
                .toStatus(SupportRequestStatus.PENDING)
                .changedById(internUserId)
                .changedByName(intern.getFullName())
                .comment("Thực tập sinh tạo yêu cầu mới")
                .build());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportRequestResponse> getMyRequests(Long internUserId) {
        return supportRequestRepository.findByInternUserIdOrderByCreatedAtDesc(internUserId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportRequestResponse> getAllRequests(SupportRequestStatus status, SupportRequestType requestType) {
        return supportRequestRepository.findWithFilters(status, requestType).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SupportRequestResponse cancelRequest(Long id, Long internUserId) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu ID: " + id));

        if (!request.getInternUserId().equals(internUserId)) {
            throw new ForbiddenException("Bạn không có quyền hủy yêu cầu của người khác");
        }

        if (request.getStatus() != SupportRequestStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể hủy yêu cầu khi đang ở trạng thái PENDING (chưa được tiếp nhận xử lý)");
        }

        SupportRequestStatus oldStatus = request.getStatus();
        request.setStatus(SupportRequestStatus.CANCELLED);
        SupportRequest saved = supportRequestRepository.save(request);

        statusLogRepository.save(SupportRequestStatusLog.builder()
                .supportRequest(saved)
                .fromStatus(oldStatus)
                .toStatus(SupportRequestStatus.CANCELLED)
                .changedById(internUserId)
                .changedByName(request.getInternProfile() != null ? request.getInternProfile().getFullName() : "Intern")
                .comment("Thực tập sinh chủ động hủy yêu cầu")
                .build());

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public SupportRequestResponse processRequest(Long id, Long hrUserId, String hrUserName) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu ID: " + id));

        if (request.getStatus() != SupportRequestStatus.PENDING) {
            throw new BadRequestException("Yêu cầu không ở trạng thái PENDING");
        }

        request.setStatus(SupportRequestStatus.PROCESSING);
        request.setHandlerId(hrUserId);
        request.setHandlerName(hrUserName);
        SupportRequest saved = supportRequestRepository.save(request);

        statusLogRepository.save(SupportRequestStatusLog.builder()
                .supportRequest(saved)
                .fromStatus(SupportRequestStatus.PENDING)
                .toStatus(SupportRequestStatus.PROCESSING)
                .changedById(hrUserId)
                .changedByName(hrUserName)
                .comment("HR tiếp nhận xử lý")
                .build());

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public SupportRequestResponse respondRequest(Long id, RespondSupportRequestDto dto, MultipartFile responseAttachment, Long hrUserId, String hrUserName) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu ID: " + id));

        if (request.getStatus() == SupportRequestStatus.RESOLVED || request.getStatus() == SupportRequestStatus.REJECTED) {
            throw new BadRequestException("Yêu cầu đã được đóng trước đó, không thể phản hồi lại");
        }

        String storageKey = null;
        String originalFilename = null;
        if (responseAttachment != null && !responseAttachment.isEmpty()) {
            validateUploadedFile(responseAttachment);
            storageKey = saveFileToInternalStorage(responseAttachment, "responses");
            originalFilename = responseAttachment.getOriginalFilename();
            request.setResponseStorageKey(storageKey);
            request.setResponseFilename(originalFilename);
        }

        SupportRequestStatus oldStatus = request.getStatus();
        request.setStatus(dto.getStatus());
        request.setHrResponse(dto.getHrResponse());
        request.setHandlerId(hrUserId);
        request.setHandlerName(hrUserName);
        request.setResolvedAt(LocalDateTime.now());
        if (dto.getOverrideReason() != null) {
            request.setOverrideReason(dto.getOverrideReason());
        }

        SupportRequest saved = supportRequestRepository.save(request);

        statusLogRepository.save(SupportRequestStatusLog.builder()
                .supportRequest(saved)
                .fromStatus(oldStatus)
                .toStatus(dto.getStatus())
                .changedById(hrUserId)
                .changedByName(hrUserName)
                .comment("HR phản hồi: " + (dto.getStatus() == SupportRequestStatus.RESOLVED ? "ĐÃ DUYỆT" : "TỪ CHỐI"))
                .build());

        return mapToResponse(saved);
    }

    @Override
    public Resource loadAttachmentFile(Long id, Long requestingUserId, String requestingRole, boolean isResponseAttachment) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu ID: " + id));

        // Kiểm soát IDOR nghiêm ngặt
        boolean isOwner = request.getInternUserId().equals(requestingUserId);
        boolean isHrOrAdmin = "HR".equalsIgnoreCase(requestingRole) || "HR_MANAGER".equalsIgnoreCase(requestingRole) || "ADMIN".equalsIgnoreCase(requestingRole);

        if (!isOwner && !isHrOrAdmin) {
            throw new ForbiddenException("Bạn không có quyền truy cập file tài liệu này");
        }

        String storageKey = isResponseAttachment ? request.getResponseStorageKey() : request.getAttachmentStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new ResourceNotFoundException("Yêu cầu này không có file đính kèm");
        }

        Path rootDir = Paths.get(supportDocsDir).toAbsolutePath().normalize();
        Path filePath = rootDir.resolve(storageKey).normalize();
        if (!filePath.startsWith(rootDir)) {
            throw new ForbiddenException("Phát hiện đường dẫn file bất hợp pháp (Path Traversal Attempt)");
        }

        File file = filePath.toFile();
        if (!file.exists() || !file.canRead()) {
            throw new ResourceNotFoundException("File vật lý không tồn tại trên hệ thống lưu trữ");
        }

        // Ghi Audit Log truy cập file nhạy cảm
        documentAccessLogRepository.save(DocumentAccessLog.builder()
                .userId(requestingUserId)
                .userRole(requestingRole)
                .documentType(isResponseAttachment ? "SUPPORT_RESPONSE" : "SUPPORT_ATTACHMENT")
                .targetId(id)
                .storageKey(storageKey)
                .build());

        return new FileSystemResource(file);
    }

    @Override
    public String getAttachmentFilename(Long id, boolean isResponseAttachment) {
        return getAttachmentFilename(id, isResponseAttachment, null, null);
    }

    @Override
    public String getAttachmentFilename(Long id, boolean isResponseAttachment, Long requestingUserId, String requestingRole) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu ID: " + id));

        if (requestingUserId != null && requestingRole != null) {
            boolean isOwner = request.getInternUserId().equals(requestingUserId);
            boolean isHrOrAdmin = "HR".equalsIgnoreCase(requestingRole) || "HR_MANAGER".equalsIgnoreCase(requestingRole) || "ADMIN".equalsIgnoreCase(requestingRole);
            if (!isOwner && !isHrOrAdmin) {
                throw new ForbiddenException("Bạn không có quyền truy cập file tài liệu này");
            }
        }

        return isResponseAttachment ? request.getResponseFilename() : request.getAttachmentFilename();
    }

    private void validateInternEligibilityForRequest(InternProfile intern, SupportRequestType type) {
        Optional<Contract> latestContractOpt = contractRepository.findFirstByInternProfileIdOrderByCreatedAtDesc(intern.getId());

        switch (type) {
            case INTERNSHIP_CONFIRMATION:
                boolean hasActiveContract = latestContractOpt.isPresent() &&
                        (latestContractOpt.get().getStatus() == DynamicContractStatus.ACTIVE || latestContractOpt.get().getStatus() == DynamicContractStatus.SIGNED);
                if (!hasActiveContract) {
                    throw new BadRequestException("Giấy xác nhận đang thực tập yêu cầu TTS phải có hợp đồng ACTIVE hoặc SIGNED.");
                }
                break;
            case COMPLETION_CERTIFICATE:
            case RECOMMENDATION_LETTER:
                boolean isCompleted = intern.getStatus() == InternStatus.COMPLETED ||
                        (latestContractOpt.isPresent() && latestContractOpt.get().getStatus() == DynamicContractStatus.EXPIRED);
                if (!isCompleted) {
                    throw new BadRequestException("Chứng nhận hoàn thành hoặc thư giới thiệu chỉ áp dụng cho TTS đã kết thúc kỳ thực tập (COMPLETED / EXPIRED).");
                }
                break;
            case UNIVERSITY_CONFIRMATION:
            case ALLOWANCE_INQUIRY:
            case OTHER:
                // Chấp nhận cả đang thực tập và đã hoàn thành
                break;
        }
    }

    private void validateUploadedFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Dung lượng file vượt quá giới hạn cho phép (tối đa 5 MB)");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new BadRequestException("Định dạng file không hợp lệ");
        }

        String ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BadRequestException("Định dạng file không được hỗ trợ. Chỉ chấp nhận: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        // Kiểm tra Magic Bytes cơ bản
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 4) {
                throw new BadRequestException("Nội dung file rỗng hoặc không hợp lệ");
            }
            if ("pdf".equals(ext) && (bytes[0] != 0x25 || bytes[1] != 0x50 || bytes[2] != 0x44 || bytes[3] != 0x46)) { // %PDF
                throw new BadRequestException("Header nội dung không đúng định dạng PDF chuẩn");
            }
        } catch (IOException e) {
            throw new BadRequestException("Không thể đọc nội dung file: " + e.getMessage());
        }
    }

    private String saveFileToInternalStorage(MultipartFile file, String subfolder) {
        try {
            Path rootPath = Paths.get(supportDocsDir).toAbsolutePath().normalize();
            Path dirPath = rootPath.resolve(subfolder).normalize();
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            String randomFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path targetPath = dirPath.resolve(randomFileName).normalize();
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return subfolder + "/" + randomFileName;
        } catch (IOException e) {
            log.error("Loi luu tru file tai lieu ho tro: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể lưu trữ file vào máy chủ: " + e.getMessage());
        }
    }

    private LocalDateTime calculateSlaDueDate(LocalDateTime start, int businessDays) {
        LocalDateTime current = start;
        int added = 0;
        while (added < businessDays) {
            current = current.plusDays(1);
            if (WorkingCalendarHelper.isWorkingDay(current.toLocalDate())) {
                added++;
            }
        }
        return current;
    }

    private SupportRequestResponse mapToResponse(SupportRequest r) {
        return SupportRequestResponse.builder()
                .id(r.getId())
                .internUserId(r.getInternUserId())
                .internProfileId(r.getInternProfile() != null ? r.getInternProfile().getId() : null)
                .internFullName(r.getInternProfile() != null ? r.getInternProfile().getFullName() : null)
                .internEmail(r.getInternProfile() != null ? r.getInternProfile().getEmail() : null)
                .requestType(r.getRequestType())
                .title(r.getTitle())
                .content(r.getContent())
                .hasAttachment(r.getAttachmentStorageKey() != null)
                .attachmentFilename(r.getAttachmentFilename())
                .status(r.getStatus())
                .hrResponse(r.getHrResponse())
                .hasResponseAttachment(r.getResponseStorageKey() != null)
                .responseFilename(r.getResponseFilename())
                .handlerId(r.getHandlerId())
                .handlerName(r.getHandlerName())
                .overrideReason(r.getOverrideReason())
                .slaDueDate(r.getSlaDueDate())
                .isOverdueSla(r.getStatus() != SupportRequestStatus.RESOLVED && r.getStatus() != SupportRequestStatus.REJECTED
                        && LocalDateTime.now().isAfter(r.getSlaDueDate()))
                .resolvedAt(r.getResolvedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
