package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.storage.FileStorageService;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.FileServiceClient;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.dto.request.ConfirmContractRequest;
import org.example.internservice.intern.dto.request.ContractFeedbackRequest;
import org.example.internservice.intern.dto.request.ContractStorageDtos;
import org.example.internservice.intern.dto.request.RejectContractRequest;
import org.example.internservice.intern.dto.request.StorageBusinessDtos;
import org.example.internservice.intern.dto.request.TerminateContractRequest;
import org.example.internservice.intern.dto.request.UploadContractRequest;
import org.example.internservice.intern.dto.response.ContractResponse;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.intern.entity.InternContract;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.ContractStatus;
import org.example.internservice.intern.entity.enums.ContractType;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternContractRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.service.InternContractService;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternContractServiceImpl implements InternContractService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".pdf", ".docx", ".doc");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/octet-stream"
    );

    private final InternProfileRepository internProfileRepository;
    private final InternContractRepository internContractRepository;
    private final FileStorageService fileStorageService;
    private final FileServiceClient fileServiceClient;
    private final org.example.internservice.intern.client.IntegrationEmailClient integrationEmailClient;
    private final org.example.internservice.intern.client.NotificationEventDispatcher notificationEventDispatcher;
    private final org.example.internservice.intern.client.IdentityServiceClient identityServiceClient;

    @Override
    @Transactional
    public ContractResponse uploadContract(String internCode, MultipartFile file, UploadContractRequest request, String uploadedBy) {
        log.info("Bắt đầu xử lý tải lên hợp đồng (multipart): internCode={}, uploadedBy={}", internCode, uploadedBy);
        validateFile(file);
        validateContractRequest(request);

        InternProfile internProfile = getAndValidateInternProfile(internCode);
        String contractNumber = resolveContractNumber(request.getContractNumber());

        InternContract parentContract = resolveParentContract(request.getParentContractId(), internProfile);
        ContractType contractType = request.getContractType() != null
                ? request.getContractType()
                : (parentContract != null ? ContractType.EXTENSION_APPENDIX : ContractType.OFFICIAL_INTERNSHIP);

        String subDirectory = "contracts/" + internProfile.getInternCode();
        String uniqueFileName = fileStorageService.storeFile(file, subDirectory);
        String relativeFilePath = subDirectory + "/" + uniqueFileName;

        InternContract contract = InternContract.builder()
                .internProfile(internProfile)
                .parentContract(parentContract)
                .contractType(contractType)
                .contractNumber(contractNumber)
                .contractTitle(request.getContractTitle().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .allowanceAmount(request.getAllowanceAmount())
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName(Objects.requireNonNullElse(file.getOriginalFilename(), "contract.pdf"))
                .fileName(uniqueFileName)
                .filePath(relativeFilePath)
                .fileSize(file.getSize())
                .contentType(StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/pdf")
                .uploadedBy(StringUtils.hasText(uploadedBy) ? uploadedBy : "HR")
                .notes(request.getNotes())
                .build();

        InternContract savedContract = saveContractWithRollback(contract, relativeFilePath);
        notifyContractCreated(savedContract, internProfile);
        return mapToContractResponse(savedContract, internProfile);
    }

    @Override
    public StorageBusinessDtos.RequestUploadUrlResponse requestContractUploadUrl(
            String internCode, ContractStorageDtos.RequestContractUploadUrlRequest request) {
        log.info("HR yêu cầu presigned S3 upload URL cho hợp đồng intern: {}", internCode);
        getAndValidateInternProfile(internCode);

        FileServiceClient.PresignedUploadRequest internalReq = FileServiceClient.PresignedUploadRequest.builder()
                .prefix("temp/contracts")
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .sizeLimitBytes(request.getFileSize())
                .build();

        FileServiceClient.PresignedUploadResponse internalRes = fileServiceClient.createPresignedUpload(internalReq);

        return StorageBusinessDtos.RequestUploadUrlResponse.builder()
                .tempKey(internalRes.getTempKey())
                .presignedUrl(internalRes.getPresignedUrl())
                .expiresInSeconds(internalRes.getExpiresInSeconds())
                .build();
    }

    @Override
    @Transactional
    public ContractResponse confirmContractUpload(
            String internCode, ContractStorageDtos.ConfirmContractUploadRequest request, String uploadedBy) {
        log.info("Xác nhận upload S3 thành công cho hợp đồng intern: {}, tempKey: {}", internCode, request.getTempKey());
        InternProfile internProfile = getAndValidateInternProfile(internCode);

        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BadRequestException("Ngày bắt đầu và ngày kết thúc hợp đồng không được để trống");
        }
        if (request.getEndDate().isBefore(request.getStartDate()) || request.getEndDate().isEqual(request.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc hợp đồng phải sau ngày bắt đầu");
        }

        String contractNumber = resolveContractNumber(request.getContractNumber());
        InternContract parentContract = resolveParentContract(request.getParentContractId(), internProfile);
        ContractType contractType = request.getContractType() != null
                ? request.getContractType()
                : (parentContract != null ? ContractType.EXTENSION_APPENDIX : ContractType.OFFICIAL_INTERNSHIP);

        String extension = ".pdf";
        if (request.getOriginalFileName().contains(".")) {
            extension = request.getOriginalFileName().substring(request.getOriginalFileName().lastIndexOf("."));
        }
        String destKey = String.format("contracts/%s/%s_%d_%s%s",
                internCode, contractNumber, System.currentTimeMillis(), UUID.randomUUID().toString().substring(0, 8), extension);

        FileServiceClient.PromoteFileRequest promoteReq = FileServiceClient.PromoteFileRequest.builder()
                .tempKey(request.getTempKey())
                .destinationKey(destKey)
                .build();

        FileServiceClient.PromoteFileResponse promoteRes = fileServiceClient.promoteFile(promoteReq);

        InternContract contract = InternContract.builder()
                .internProfile(internProfile)
                .parentContract(parentContract)
                .contractType(contractType)
                .contractNumber(contractNumber)
                .contractTitle(request.getContractTitle().trim())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .allowanceAmount(request.getAllowanceAmount())
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName(request.getOriginalFileName())
                .fileName(destKey)
                .filePath(promoteRes.getFinalKey())
                .fileSize(promoteRes.getFileSize() != null ? promoteRes.getFileSize() : 0L)
                .contentType(promoteRes.getContentType() != null ? promoteRes.getContentType() : "application/pdf")
                .uploadedBy(StringUtils.hasText(uploadedBy) ? uploadedBy : "HR")
                .notes(request.getNotes())
                .build();

        InternContract saved = internContractRepository.save(contract);
        log.info("Lưu hợp đồng S3 vào DB thành công với ID: {}", saved.getId());

        // Gửi email thông báo mời ký hợp đồng cho TTS
        try {
            if (internProfile.getEmail() != null && !internProfile.getEmail().isBlank()) {
                java.util.Map<String, Object> emailPayload = new java.util.HashMap<>();
                emailPayload.put("contractId", saved.getId());
                emailPayload.put("recipientEmail", internProfile.getEmail());
                emailPayload.put("recipientName", internProfile.getFullName());
                emailPayload.put("contractNumber", saved.getContractNumber());
                emailPayload.put("contractTitle", saved.getContractTitle());
                emailPayload.put("contractType", saved.getContractType() != null ? saved.getContractType().name() : "OFFICIAL_INTERNSHIP");
                emailPayload.put("startDate", saved.getStartDate() != null ? saved.getStartDate().toString() : null);
                emailPayload.put("endDate", saved.getEndDate() != null ? saved.getEndDate().toString() : null);
                emailPayload.put("allowanceAmount", saved.getAllowanceAmount());
                emailPayload.put("eventType", "CONTRACT_INVITATION");
                emailPayload.put("notes", saved.getNotes());

                integrationEmailClient.sendContractNotificationEmail(emailPayload);
            }
        } catch (Exception ex) {
            log.warn("Lỗi khi gửi email mời ký hợp đồng: {}", ex.getMessage());
        }

        // Bắn thông báo real-time tới thực tập sinh
        notifyContractCreated(saved, internProfile);

        return mapToContractResponse(saved, internProfile);
    }

    @Override
    public ContractStorageDtos.ViewContractUrlResponse getContractViewUrl(Long contractId, CustomUserDetails userDetails) {
        log.info("Lấy Presigned View URL cho hợp đồng ID: {}", contractId);
        if (contractId == null || contractId <= 0) {
            throw new BadRequestException("ID hợp đồng không hợp lệ");
        }
        InternContract contract = internContractRepository.findByIdWithProfile(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng với ID: " + contractId));

        verifyContractOwnership(contract, userDetails);

        String filePath = contract.getFilePath();
        if (filePath != null && (filePath.startsWith("contracts/") || filePath.startsWith("temp/"))) {
            try {
                FileServiceClient.PresignedViewRequest viewReq = FileServiceClient.PresignedViewRequest.builder()
                        .fileKey(filePath)
                        .expiresInMinutes(30)
                        .build();

                FileServiceClient.PresignedViewResponse viewRes = fileServiceClient.createPresignedView(viewReq);
                return ContractStorageDtos.ViewContractUrlResponse.builder()
                        .contractId(contract.getId())
                        .contractNumber(contract.getContractNumber())
                        .originalFileName(contract.getOriginalFileName())
                        .presignedUrl(viewRes.getPresignedUrl())
                        .expiresInSeconds(viewRes.getExpiresInSeconds())
                        .build();
            } catch (Exception ex) {
                log.warn("Không thể sinh presigned view url từ file-service, fallback sang URL rỗng: {}", ex.getMessage());
            }
        }

        return ContractStorageDtos.ViewContractUrlResponse.builder()
                .contractId(contract.getId())
                .contractNumber(contract.getContractNumber())
                .originalFileName(contract.getOriginalFileName())
                .presignedUrl(null)
                .expiresInSeconds(0)
                .build();
    }

    @Override
    public List<ContractResponse> getAllContracts() {
        log.info("HR truy vấn danh sách toàn bộ hợp đồng công ty");
        List<InternContract> contracts = internContractRepository.findAllWithProfile();
        return contracts.stream().map(c -> mapToContractResponse(c, c.getInternProfile())).toList();
    }

    @Override
    public List<ContractResponse> getContractsByInternCode(String internCode) {
        log.info("Lấy danh sách hợp đồng cho internCode: {}", internCode);
        if (!StringUtils.hasText(internCode)) {
            throw new BadRequestException("Mã thực tập sinh không được để trống");
        }
        InternProfile profile = internProfileRepository.findByInternCode(internCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với mã: " + internCode));

        List<InternContract> contracts = internContractRepository.findByInternCodeWithProfile(profile.getInternCode());
        return contracts.stream().map(c -> mapToContractResponse(c, profile)).toList();
    }

    @Override
    public DocumentDownloadDto loadContractForDownload(Long contractId) {
        log.info("Tải tệp hợp đồng ID: {}", contractId);
        if (contractId == null || contractId <= 0) {
            throw new BadRequestException("ID hợp đồng không hợp lệ");
        }
        InternContract contract = internContractRepository.findByIdWithProfile(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng với ID: " + contractId));

        Resource resource = fileStorageService.loadFileAsResource(contract.getFilePath());
        return DocumentDownloadDto.builder()
                .resource(resource)
                .originalFileName(contract.getOriginalFileName())
                .contentType(contract.getContentType())
                .fileSize(contract.getFileSize())
                .build();
    }

    @Override
    public List<ContractResponse> getMyContracts(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập");
        }
        log.info("Lấy danh sách hợp đồng cho user: userId={}, username={}", userDetails.getUserId(), userDetails.getUsername());
        List<InternContract> contracts = internContractRepository.findAllByUserIdOrEmailWithProfile(
                userDetails.getUserId(), userDetails.getUsername());
        return contracts.stream().map(c -> mapToContractResponse(c, c.getInternProfile())).toList();
    }

    @Override
    public ContractResponse getMyActiveContract(CustomUserDetails userDetails) {
        List<ContractResponse> contracts = getMyContracts(userDetails);
        if (contracts.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy hợp đồng nào của bạn");
        }
        return contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.PENDING_SIGNATURE
                        || c.getStatus() == ContractStatus.PENDING_INTERN_FEEDBACK
                        || c.getStatus() == ContractStatus.ACTIVE
                        || c.getStatus() == ContractStatus.SIGNED)
                .findFirst()
                .orElse(contracts.get(0));
    }

    @Override
    public ContractResponse getContractById(Long contractId, CustomUserDetails userDetails) {
        log.info("Lấy chi tiết hợp đồng ID: {}", contractId);
        if (contractId == null || contractId <= 0) {
            throw new BadRequestException("ID hợp đồng không hợp lệ");
        }
        InternContract contract = internContractRepository.findByIdWithProfile(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng với ID: " + contractId));

        verifyContractOwnership(contract, userDetails);
        return mapToContractResponse(contract, contract.getInternProfile());
    }

    @Override
    @Transactional
    public ContractResponse confirmContract(Long contractId, ConfirmContractRequest request, CustomUserDetails userDetails) {
        log.info("Xác nhận ký hợp đồng ID: {} bởi user: {}", contractId, userDetails != null ? userDetails.getUsername() : "null");
        if (contractId == null || contractId <= 0) {
            throw new BadRequestException("ID hợp đồng không hợp lệ");
        }
        if (request == null || !Boolean.TRUE.equals(request.getAgreeTerms())) {
            throw new BadRequestException("Bạn phải đồng ý với các điều khoản hợp đồng để tiếp tục");
        }

        InternContract contract = internContractRepository.findByIdWithProfile(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng với ID: " + contractId));

        verifyContractOwnership(contract, userDetails);

        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE && contract.getStatus() != ContractStatus.PENDING_INTERN_FEEDBACK) {
            throw new BadRequestException("Hợp đồng này không ở trạng thái chờ ký (Trạng thái hiện tại: " + contract.getStatus() + ")");
        }

        if (LocalDate.now().isAfter(contract.getEndDate())) {
            contract.setStatus(ContractStatus.EXPIRED);
            internContractRepository.save(contract);
            throw new BadRequestException("Hợp đồng này đã hết hạn hiệu lực vào ngày " + contract.getEndDate() + ". Vui lòng liên hệ HR để nhận hợp đồng mới.");
        }

        contract.setStatus(ContractStatus.ACTIVE);
        contract.setSignedAt(LocalDateTime.now());
        contract.setSignerFullName(request.getSignerFullName().trim());
        contract.setInternConfirmationNote(request.getConfirmationNote());

        // Nếu đây là phụ lục gia hạn (có parentContract), chuyển trạng thái HĐ cũ sang SUPERSEDED
        if (contract.getParentContract() != null) {
            InternContract parent = contract.getParentContract();
            log.info("Hợp đồng ID [{}] là phụ lục gia hạn của HĐ ID [{}], chuyển HĐ cha sang SUPERSEDED", contract.getId(), parent.getId());
            parent.setStatus(ContractStatus.SUPERSEDED);
            internContractRepository.save(parent);
        }

        InternContract savedContract = internContractRepository.save(contract);

        // Cập nhật InternProfile: chuyển status sang INTERNING và đồng bộ ngày kết thúc thực tập mới nhất
        InternProfile profile = contract.getInternProfile();
        log.info("Chuyển trạng thái intern [{}] sang INTERNING và cập nhật endDate thành [{}]", profile.getInternCode(), contract.getEndDate());
        profile.setStatus(InternStatus.INTERNING);
        profile.setEndDate(contract.getEndDate());
        internProfileRepository.save(profile);

        // Bắn thông báo real-time tới HR
        notifyContractSignedByIntern(savedContract, profile);

        return mapToContractResponse(savedContract, profile);
    }

    @Override
    @Transactional
    public ContractResponse submitFeedback(Long contractId, ContractFeedbackRequest request, CustomUserDetails userDetails) {
        log.info("Thực tập sinh gửi phản hồi thắc mắc cho hợp đồng ID: {}", contractId);
        if (contractId == null || contractId <= 0) {
            throw new BadRequestException("ID hợp đồng không hợp lệ");
        }
        if (request == null || !StringUtils.hasText(request.getFeedbackNotes())) {
            throw new BadRequestException("Nội dung phản hồi không được để trống");
        }

        InternContract contract = internContractRepository.findByIdWithProfile(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng với ID: " + contractId));

        verifyContractOwnership(contract, userDetails);

        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE && contract.getStatus() != ContractStatus.PENDING_INTERN_FEEDBACK) {
            throw new BadRequestException("Chỉ có thể gửi thắc mắc khi hợp đồng đang ở trạng thái chờ ký hoặc chờ phản hồi");
        }

        contract.setStatus(ContractStatus.PENDING_INTERN_FEEDBACK);
        contract.setFeedbackNotes(request.getFeedbackNotes().trim());
        contract.setFeedbackAt(LocalDateTime.now());
        InternContract saved = internContractRepository.save(contract);

        log.info("Đã lưu thắc mắc của TTS cho hợp đồng ID [{}]. Hệ thống tự động ghi nhận để HR xử lý.", contractId);
        return mapToContractResponse(saved, contract.getInternProfile());
    }

    @Override
    @Transactional
    public ContractResponse terminateContract(Long contractId, TerminateContractRequest request, String terminatedBy) {
        log.info("HR [{}] yêu cầu chấm dứt hợp đồng ID: {}", terminatedBy, contractId);
        if (contractId == null || contractId <= 0) {
            throw new BadRequestException("ID hợp đồng không hợp lệ");
        }
        if (request == null || !StringUtils.hasText(request.getTerminationReason())) {
            throw new BadRequestException("Lý do chấm dứt hợp đồng không được để trống");
        }

        InternContract contract = internContractRepository.findByIdWithProfile(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng với ID: " + contractId));

        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new BadRequestException("Hợp đồng này đã bị chấm dứt trước đó");
        }

        contract.setStatus(ContractStatus.TERMINATED);
        contract.setTerminationReason(request.getTerminationReason().trim());
        contract.setTerminatedAt(LocalDateTime.now());
        contract.setTerminatedBy(StringUtils.hasText(terminatedBy) ? terminatedBy : "HR");
        InternContract saved = internContractRepository.save(contract);

        log.info("Hợp đồng [{}] đã được chấm dứt hợp lệ và lưu vết kiểm toán (Audit Trail). Không thực hiện xóa vật lý.", contract.getContractNumber());

        // Bắn thông báo real-time chấm dứt hợp đồng
        notifyContractTerminated(saved, contract.getInternProfile());

        return mapToContractResponse(saved, contract.getInternProfile());
    }

    @Override
    @Transactional
    public void sendContractReminder(Long contractId, String sentBy) {
        log.info("HR [{}] gửi nhắc nhở ký cho hợp đồng ID: {}", sentBy, contractId);
        InternContract contract = internContractRepository.findByIdWithProfile(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng với ID: " + contractId));

        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE && contract.getStatus() != ContractStatus.PENDING_INTERN_FEEDBACK) {
            throw new BadRequestException("Chỉ có thể gửi nhắc nhở khi hợp đồng đang ở trạng thái chờ ký hoặc chờ phản hồi");
        }

        InternProfile profile = contract.getInternProfile();
        log.info("Đã phát lệnh nhắc nhở ký hợp đồng [{}] tới thực tập sinh [{}] (Email: {})",
                contract.getContractNumber(), profile.getFullName(), profile.getEmail());

        // Cập nhật vết nhắc nhở
        contract.setLastRemindedAt(LocalDateTime.now());
        contract.setReminderCount(contract.getReminderCount() != null ? contract.getReminderCount() + 1 : 1);
        internContractRepository.save(contract);

        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            java.util.Map<String, Object> emailPayload = new java.util.HashMap<>();
            emailPayload.put("contractId", contract.getId());
            emailPayload.put("recipientEmail", profile.getEmail());
            emailPayload.put("recipientName", profile.getFullName());
            emailPayload.put("contractNumber", contract.getContractNumber());
            emailPayload.put("contractTitle", contract.getContractTitle());
            emailPayload.put("contractType", contract.getContractType() != null ? contract.getContractType().name() : "OFFICIAL_INTERNSHIP");
            emailPayload.put("startDate", contract.getStartDate() != null ? contract.getStartDate().toString() : null);
            emailPayload.put("endDate", contract.getEndDate() != null ? contract.getEndDate().toString() : null);
            emailPayload.put("allowanceAmount", contract.getAllowanceAmount());
            emailPayload.put("eventType", "CONTRACT_REMINDER");
            emailPayload.put("notes", contract.getNotes());

            integrationEmailClient.sendContractNotificationEmail(emailPayload);
        }

        // Bắn thông báo real-time nhắc nhở ký hợp đồng
        notifyContractReminder(contract, profile);
    }

    @Override
    @Transactional
    public int scanAndSendPendingContractReminders(int overdueDays, int cooldownDays) {
        LocalDateTime createdBefore = LocalDateTime.now().minusDays(overdueDays);
        LocalDateTime remindedBefore = LocalDateTime.now().minusDays(cooldownDays);

        log.info("[CRON-CONTRACT] Bắt đầu quét hợp đồng chờ ký: createdBefore={}, remindedBefore={}",
                createdBefore, remindedBefore);

        List<InternContract> pendingContracts = internContractRepository.findContractsNeedingSignatureReminder(
                ContractStatus.PENDING_SIGNATURE, createdBefore, remindedBefore);

        log.info("[CRON-CONTRACT] Phát hiện {} hợp đồng PENDING_SIGNATURE cần gửi email nhắc nhở tự động",
                pendingContracts.size());

        int count = 0;
        for (InternContract contract : pendingContracts) {
            try {
                InternProfile profile = contract.getInternProfile();
                if (profile != null && profile.getEmail() != null && !profile.getEmail().isBlank()) {
                    java.util.Map<String, Object> emailPayload = new java.util.HashMap<>();
                    emailPayload.put("contractId", contract.getId());
                    emailPayload.put("recipientEmail", profile.getEmail());
                    emailPayload.put("recipientName", profile.getFullName());
                    emailPayload.put("contractNumber", contract.getContractNumber());
                    emailPayload.put("contractTitle", contract.getContractTitle());
                    emailPayload.put("contractType", contract.getContractType() != null ? contract.getContractType().name() : "OFFICIAL_INTERNSHIP");
                    emailPayload.put("startDate", contract.getStartDate() != null ? contract.getStartDate().toString() : null);
                    emailPayload.put("endDate", contract.getEndDate() != null ? contract.getEndDate().toString() : null);
                    emailPayload.put("allowanceAmount", contract.getAllowanceAmount());
                    emailPayload.put("eventType", "CONTRACT_REMINDER");
                    emailPayload.put("notes", contract.getNotes());

                    integrationEmailClient.sendContractNotificationEmail(emailPayload);

                    contract.setLastRemindedAt(LocalDateTime.now());
                    contract.setReminderCount(contract.getReminderCount() != null ? contract.getReminderCount() + 1 : 1);
                    internContractRepository.save(contract);
                    count++;
                }
            } catch (Exception e) {
                log.error("[CRON-CONTRACT] Lỗi khi gửi email nhắc nhở tự động cho hợp đồng ID: {}. Lỗi: {}",
                        contract.getId(), e.getMessage());
            }
        }

        log.info("[CRON-CONTRACT] Đã hoàn tất quét và gửi email nhắc nhở cho {} hợp đồng.", count);
        return count;
    }

    @Override
    @Transactional
    public ContractResponse rejectContract(Long contractId, RejectContractRequest request, CustomUserDetails userDetails) {
        log.info("Từ chối hợp đồng ID: {} bởi user: {}", contractId, userDetails != null ? userDetails.getUsername() : "null");
        if (contractId == null || contractId <= 0) {
            throw new BadRequestException("ID hợp đồng không hợp lệ");
        }
        if (request == null || !StringUtils.hasText(request.getRejectionReason())) {
            throw new BadRequestException("Lý do từ chối hợp đồng không được để trống");
        }
        if (request.getRejectionReason().trim().length() < 10) {
            throw new BadRequestException("Lý do từ chối hợp đồng phải từ 10 ký tự trở lên");
        }

        InternContract contract = internContractRepository.findByIdWithProfile(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng với ID: " + contractId));

        verifyContractOwnership(contract, userDetails);

        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE && contract.getStatus() != ContractStatus.PENDING_INTERN_FEEDBACK) {
            throw new BadRequestException("Hợp đồng này không ở trạng thái chờ ký (Trạng thái hiện tại: " + contract.getStatus() + ")");
        }

        contract.setStatus(ContractStatus.REJECTED_BY_INTERN);
        contract.setRejectionReason(request.getRejectionReason().trim());
        InternContract savedContract = internContractRepository.save(contract);

        return mapToContractResponse(savedContract, contract.getInternProfile());
    }

    private InternContract resolveParentContract(Long parentContractId, InternProfile internProfile) {
        if (parentContractId == null || parentContractId <= 0) {
            return null;
        }
        InternContract parent = internContractRepository.findByIdWithProfile(parentContractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng gốc để gia hạn với ID: " + parentContractId));

        if (!parent.getInternProfile().getId().equals(internProfile.getId())) {
            throw new BadRequestException("Hợp đồng gốc không thuộc về thực tập sinh này");
        }
        return parent;
    }

    private void verifyContractOwnership(InternContract contract, CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new AccessDeniedException("Yêu cầu đăng nhập");
        }
        boolean isPrivileged = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_HR")
                        || a.getAuthority().equalsIgnoreCase("HR")
                        || a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                        || a.getAuthority().equalsIgnoreCase("ADMIN")
                        || a.getAuthority().equalsIgnoreCase("ROLE_MENTOR")
                        || a.getAuthority().equalsIgnoreCase("MENTOR"));
        if (isPrivileged) {
            return;
        }

        InternProfile profile = contract.getInternProfile();
        boolean isOwner = (profile.getUserId() != null && profile.getUserId().equals(userDetails.getUserId()))
                || (profile.getId() != null && profile.getId().equals(userDetails.getUserId()))
                || (profile.getEmail() != null && profile.getEmail().equalsIgnoreCase(userDetails.getUsername()))
                || (profile.getInternCode() != null && profile.getInternCode().equalsIgnoreCase(userDetails.getUsername()));
        if (!isOwner) {
            log.warn("IDOR Blocked: User [{}] cố ý truy cập trái phép hợp đồng ID [{}] của intern [{}]",
                    userDetails.getUsername(), contract.getId(), profile.getInternCode());
            throw new AccessDeniedException("Bạn không có quyền thao tác trên hợp đồng này");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tin hợp đồng không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Dung lượng tệp tin vượt quá giới hạn cho phép (tối đa 10MB)");
        }
        String rawOriginalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        String extension = StringUtils.getFilenameExtension(rawOriginalFilename);
        String fileExtension = StringUtils.hasText(extension) ? "." + extension.toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new BadRequestException("Định dạng tệp tin không hợp lệ. Chỉ chấp nhận các định dạng: .pdf, .docx, .doc");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("MIME type không thuộc whitelist: {}", contentType);
            throw new BadRequestException("Định dạng MIME của tệp tin không được hỗ trợ");
        }
    }

    private void validateContractRequest(UploadContractRequest request) {
        if (request == null) {
            throw new BadRequestException("Dữ liệu thông tin hợp đồng không được để trống");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BadRequestException("Ngày bắt đầu và ngày kết thúc hợp đồng không được để trống");
        }
        if (request.getEndDate().isBefore(request.getStartDate()) || request.getEndDate().isEqual(request.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc hợp đồng phải sau ngày bắt đầu");
        }
    }

    private InternProfile getAndValidateInternProfile(String internCode) {
        if (!StringUtils.hasText(internCode)) {
            throw new BadRequestException("Mã thực tập sinh không được để trống");
        }
        InternProfile internProfile = internProfileRepository.findByInternCode(internCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh với mã: " + internCode));

        if (internProfile.getStatus() != InternStatus.APPROVED && internProfile.getStatus() != InternStatus.INTERNING) {
            log.warn("Từ chối tải hợp đồng: internCode={}, status={}", internCode, internProfile.getStatus());
            throw new BadRequestException("Chỉ có thể tải lên hợp đồng cho thực tập sinh đã được phê duyệt tiếp nhận (APPROVED) hoặc đang thực tập (INTERNING). Trạng thái hiện tại: " + internProfile.getStatus());
        }
        return internProfile;
    }

    private String resolveContractNumber(String providedNumber) {
        if (StringUtils.hasText(providedNumber)) {
            String trimmedNumber = providedNumber.trim();
            if (internContractRepository.existsByContractNumber(trimmedNumber)) {
                throw new DuplicateResourceException("Mã hợp đồng '" + trimmedNumber + "' đã tồn tại trên hệ thống");
            }
            return trimmedNumber;
        }

        String prefix = "HDTT-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        List<String> existingNumbers = internContractRepository.findContractNumbersByPrefix(prefix + "%");
        int nextSeq = 1;
        if (!existingNumbers.isEmpty()) {
            String latestNumber = existingNumbers.get(0);
            try {
                String seqStr = latestNumber.substring(latestNumber.lastIndexOf('-') + 1);
                nextSeq = Integer.parseInt(seqStr) + 1;
            } catch (Exception ex) {
                log.warn("Không thể parse số thứ tự từ mã hợp đồng cũ: {}", latestNumber);
                nextSeq = existingNumbers.size() + 1;
            }
        }

        String generatedNumber = String.format("%s%04d", prefix, nextSeq);
        while (internContractRepository.existsByContractNumber(generatedNumber)) {
            nextSeq++;
            generatedNumber = String.format("%s%04d", prefix, nextSeq);
        }
        return generatedNumber;
    }

    private InternContract saveContractWithRollback(InternContract contract, String relativeFilePath) {
        try {
            InternContract saved = internContractRepository.save(contract);
            log.info("Lưu metadata hợp đồng ID: {} thành công cho intern: {}", saved.getId(), contract.getInternProfile().getInternCode());
            return saved;
        } catch (Exception ex) {
            log.error("Lỗi khi lưu metadata hợp đồng vào DB, tiến hành rollback xóa file vật lý: {}", relativeFilePath, ex);
            fileStorageService.deleteFile(relativeFilePath);
            throw new RuntimeException("Lỗi lưu trữ thông tin hợp đồng. Vui lòng thử lại sau.", ex);
        }
    }

    private ContractResponse mapToContractResponse(InternContract contract, InternProfile profile) {
        Long daysRemaining = null;
        if (contract.getEndDate() != null) {
            daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), contract.getEndDate());
        }

        return ContractResponse.builder()
                .id(contract.getId())
                .parentContractId(contract.getParentContract() != null ? contract.getParentContract().getId() : null)
                .parentContractNumber(contract.getParentContract() != null ? contract.getParentContract().getContractNumber() : null)
                .contractType(contract.getContractType())
                .internCode(profile != null ? profile.getInternCode() : null)
                .internFullName(profile != null ? profile.getFullName() : null)
                .internEmail(profile != null ? profile.getEmail() : null)
                .contractNumber(contract.getContractNumber())
                .contractTitle(contract.getContractTitle())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .allowanceAmount(contract.getAllowanceAmount())
                .status(contract.getStatus())
                .originalFileName(contract.getOriginalFileName())
                .fileSize(contract.getFileSize())
                .contentType(contract.getContentType())
                .uploadedBy(contract.getUploadedBy())
                .signedAt(contract.getSignedAt())
                .signerFullName(contract.getSignerFullName())
                .internConfirmationNote(contract.getInternConfirmationNote())
                .rejectionReason(contract.getRejectionReason())
                .feedbackNotes(contract.getFeedbackNotes())
                .feedbackAt(contract.getFeedbackAt())
                .terminationReason(contract.getTerminationReason())
                .terminatedAt(contract.getTerminatedAt())
                .terminatedBy(contract.getTerminatedBy())
                .notes(contract.getNotes())
                .internProfileStatus(profile != null ? profile.getStatus() : null)
                .daysRemaining(daysRemaining)
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }

    private void notifyContractCreated(InternContract contract, InternProfile profile) {
        Long recipientUserId = resolveInternUserId(profile);
        if (recipientUserId == null) {
            return;
        }
        CreateNotificationInternalRequest notif = CreateNotificationInternalRequest.builder()
                .recipientId(recipientUserId)
                .title("Hợp đồng thực tập mới")
                .content(String.format("HR đã gửi hợp đồng %s (%s). Vui lòng kiểm tra và ký xác nhận trực tuyến.",
                        contract.getContractNumber(), contract.getContractTitle()))
                .type("CONTRACT_CREATED")
                .referenceType("CONTRACT")
                .referenceId(String.valueOf(contract.getId()))
                .actionUrl("/profile?tab=contract")
                .build();
        notificationEventDispatcher.dispatch(notif);
    }

    private void notifyContractSignedByIntern(InternContract contract, InternProfile profile) {
        List<Long> hrUserIds = identityServiceClient.findUserIdsByRole("HR");
        if (hrUserIds.isEmpty()) {
            hrUserIds = identityServiceClient.findUserIdsByRole("ADMIN");
        }
        notificationEventDispatcher.dispatchToMultiple(hrUserIds, hrId -> CreateNotificationInternalRequest.builder()
                .recipientId(hrId)
                .actorId(profile.getUserId())
                .title("Thực tập sinh đã ký hợp đồng")
                .content(String.format("TTS %s (%s) đã ký xác nhận hợp đồng %s.",
                        profile.getFullName(), profile.getInternCode(), contract.getContractNumber()))
                .type("CONTRACT_SIGNED_BY_INTERN")
                .referenceType("CONTRACT")
                .referenceId(String.valueOf(contract.getId()))
                .actionUrl("/hr/contracts/" + contract.getId())
                .build());
    }

    private void notifyContractTerminated(InternContract contract, InternProfile profile) {
        Long recipientUserId = resolveInternUserId(profile);
        if (recipientUserId != null) {
            CreateNotificationInternalRequest notif = CreateNotificationInternalRequest.builder()
                    .recipientId(recipientUserId)
                    .title("Hợp đồng thực tập đã chấm dứt")
                    .content(String.format("Hợp đồng %s đã được chấm dứt. Lý do: %s",
                            contract.getContractNumber(), contract.getTerminationReason()))
                    .type("CONTRACT_TERMINATED")
                    .referenceType("CONTRACT")
                    .referenceId(String.valueOf(contract.getId()))
                    .actionUrl("/profile?tab=contract")
                    .build();
            notificationEventDispatcher.dispatch(notif);
        }
    }

    private void notifyContractReminder(InternContract contract, InternProfile profile) {
        Long recipientUserId = resolveInternUserId(profile);
        if (recipientUserId != null) {
            CreateNotificationInternalRequest notif = CreateNotificationInternalRequest.builder()
                    .recipientId(recipientUserId)
                    .title("Nhắc nhở ký hợp đồng thực tập")
                    .content(String.format("Hợp đồng %s (%s) đang chờ bạn ký xác nhận. Vui lòng hoàn tất sớm.",
                            contract.getContractNumber(), contract.getContractTitle()))
                    .type("CONTRACT_REMINDER")
                    .referenceType("CONTRACT")
                    .referenceId(String.valueOf(contract.getId()))
                    .actionUrl("/profile?tab=contract")
                    .build();
            notificationEventDispatcher.dispatch(notif);
        }
    }

    private Long resolveInternUserId(InternProfile profile) {
        if (profile == null) {
            return null;
        }
        if (profile.getUserId() != null) {
            return profile.getUserId();
        }
        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            Long fetchedId = identityServiceClient.findUserIdByEmail(profile.getEmail());
            if (fetchedId != null) {
                profile.setUserId(fetchedId);
                internProfileRepository.save(profile);
                return fetchedId;
            }
        }
        return null;
    }
}
