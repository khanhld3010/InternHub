package org.example.internservice.intern.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.storage.FileStorageService;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.dto.request.UploadContractRequest;
import org.example.internservice.intern.dto.response.ContractResponse;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.intern.entity.InternContract;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.ContractStatus;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternContractRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.service.InternContractService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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

    @Override
    @Transactional
    public ContractResponse uploadContract(String internCode, MultipartFile file, UploadContractRequest request, String uploadedBy) {
        log.info("Bắt đầu xử lý tải lên hợp đồng: internCode={}, uploadedBy={}", internCode, uploadedBy);

        validateFile(file);
        validateContractRequest(request);

        InternProfile internProfile = getAndValidateInternProfile(internCode);
        String contractNumber = resolveContractNumber(request.getContractNumber());

        String subDirectory = "contracts/" + internProfile.getInternCode();
        String uniqueFileName = fileStorageService.storeFile(file, subDirectory);
        String relativeFilePath = subDirectory + "/" + uniqueFileName;

        InternContract contract = InternContract.builder()
                .internProfile(internProfile)
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
        return mapToContractResponse(savedContract, internProfile);
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
        return contracts.stream()
                .map(c -> mapToContractResponse(c, profile))
                .toList();
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
        return ContractResponse.builder()
                .id(contract.getId())
                .internCode(profile.getInternCode())
                .internFullName(profile.getFullName())
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
                .notes(contract.getNotes())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }
}
