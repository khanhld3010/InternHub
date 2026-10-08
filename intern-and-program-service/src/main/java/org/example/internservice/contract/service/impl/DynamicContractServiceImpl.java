package org.example.internservice.contract.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.contract.dto.request.ChangeRequestDto;
import org.example.internservice.contract.dto.request.ConfirmRevisionRequest;
import org.example.internservice.contract.dto.request.CreateContractDraftRequest;
import org.example.internservice.contract.dto.request.SignContractRequest;
import org.example.internservice.contract.dto.response.DynamicContractResponse;
import org.example.internservice.contract.entity.Contract;
import org.example.internservice.contract.entity.ContractAuditLog;
import org.example.internservice.contract.entity.ContractRevision;
import org.example.internservice.contract.entity.ContractSignature;
import org.example.internservice.contract.entity.ContractTemplate;
import org.example.internservice.contract.entity.ContractTemplateVersion;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;
import org.example.internservice.contract.repository.ContractAuditLogRepository;
import org.example.internservice.contract.repository.ContractRevisionRepository;
import org.example.internservice.contract.repository.ContractSignatureRepository;
import org.example.internservice.contract.repository.ContractTemplateRepository;
import org.example.internservice.contract.repository.ContractTemplateVersionRepository;
import org.example.internservice.contract.repository.DynamicContractRepository;
import org.example.internservice.contract.service.ContractPdfRendererService;
import org.example.internservice.contract.service.DynamicContractService;
import org.example.internservice.contract.util.TemplateSanitizerAndValidator;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicContractServiceImpl implements DynamicContractService {

    private final DynamicContractRepository contractRepository;
    private final ContractRevisionRepository revisionRepository;
    private final ContractTemplateRepository templateRepository;
    private final ContractTemplateVersionRepository templateVersionRepository;
    private final ContractSignatureRepository signatureRepository;
    private final ContractAuditLogRepository auditLogRepository;
    private final InternProfileRepository internProfileRepository;
    private final InternshipProgramRepository programRepository;
    private final TemplateSanitizerAndValidator sanitizerAndValidator;
    private final ObjectMapper objectMapper;
    private final ContractPdfRendererService pdfRendererService;
    private final org.example.internservice.intern.client.FileServiceClient fileServiceClient;

    @Override
    @Transactional
    public DynamicContractResponse createDraft(CreateContractDraftRequest request, String createdBy) {
        InternProfile internProfile = internProfileRepository.findById(request.getInternId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thực tập sinh với ID: " + request.getInternId()));

        InternshipProgram program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chương trình thực tập với ID: " + request.getProgramId()));

        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu hợp đồng với ID: " + request.getTemplateId()));

        ContractTemplateVersion templateVersion;
        if (request.getTemplateVersionNumber() != null) {
            templateVersion = templateVersionRepository.findByContractTemplateIdAndVersionNumber(template.getId(), request.getTemplateVersionNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên bản mẫu hợp đồng số: " + request.getTemplateVersionNumber()));
        } else {
            templateVersion = template.getVersions().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Mẫu hợp đồng chưa có bất kỳ phiên bản nào"));
        }

        String contractNumber = "HDTT-" + System.currentTimeMillis();

        Contract contract = Contract.builder()
                .contractNumber(contractNumber)
                .internProfile(internProfile)
                .internshipProgram(program)
                .status(DynamicContractStatus.DRAFT)
                .effectiveFrom(request.getStartDate())
                .effectiveTo(request.getEndDate())
                .allowanceAmount(request.getAllowanceAmount())
                .createdBy(createdBy)
                .build();
        contract = contractRepository.save(contract);

        Map<String, String> variables = buildVariablesMap(internProfile, contractNumber, request);
        String variablesJson = writeJson(variables);

        sanitizerAndValidator.validateTemplatePlaceholders(templateVersion.getContentTemplate());
        String canonicalHtml = sanitizerAndValidator.renderCanonicalSnapshot(templateVersion.getContentTemplate(), variables);
        String snapshotHash = sanitizerAndValidator.calculateSha256(canonicalHtml);

        ContractRevision revision = ContractRevision.builder()
                .contract(contract)
                .revisionNumber(1)
                .templateVersion(templateVersion)
                .variablesPayload(variablesJson)
                .canonicalSnapshotContent(canonicalHtml)
                .snapshotHash(snapshotHash)
                .status(DynamicContractStatus.DRAFT)
                .createdBy(createdBy)
                .build();
        revision = revisionRepository.save(revision);

        contract.setCurrentRevisionId(revision.getId());
        contractRepository.save(contract);

        logAudit(contract.getId(), revision.getId(), 0L, "HR", "CONTRACT_CREATED", Map.of("revision", 1));

        return toResponse(contract, revision);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> previewDraft(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + contractId));
        ContractRevision revision = revisionRepository.findById(contract.getCurrentRevisionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản sửa đổi hiện tại"));

        return Map.of(
                "canonicalHtml", revision.getCanonicalSnapshotContent(),
                "snapshotHash", revision.getSnapshotHash() != null ? revision.getSnapshotHash() : "",
                "status", revision.getStatus().name()
        );
    }

    @Override
    @Transactional
    public DynamicContractResponse sendContract(Long contractId, String hrUsername) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + contractId));
        ContractRevision revision = revisionRepository.findById(contract.getCurrentRevisionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản sửa đổi hiện tại"));

        if (contract.getStatus() != DynamicContractStatus.DRAFT && contract.getStatus() != DynamicContractStatus.HR_REVISING) {
            throw new IllegalStateException("Hợp đồng không ở trạng thái hợp lệ để gửi: " + contract.getStatus());
        }

        contract.setStatus(DynamicContractStatus.SENT);
        revision.setStatus(DynamicContractStatus.SENT);

        contractRepository.save(contract);
        revisionRepository.save(revision);

        logAudit(contract.getId(), revision.getId(), 0L, "HR", "SENT", Map.of("sender", hrUsername));

        return toResponse(contract, revision);
    }

    @Override
    @Transactional(readOnly = true)
    public DynamicContractResponse getMyContract(Long internUserId) {
        InternProfile internProfile = internProfileRepository.findByUserId(internUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh của bạn"));

        Contract contract = contractRepository.findFirstByInternProfileIdOrderByCreatedAtDesc(internProfile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa có hợp đồng thực tập nào"));

        ContractRevision revision = revisionRepository.findById(contract.getCurrentRevisionId())
                .orElse(null);

        return toResponse(contract, revision);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> previewRevision(Long contractId, Long revisionId, Long internUserId) {
        ContractRevision revision = validateInternOwnership(contractId, revisionId, internUserId);
        return Map.of(
                "canonicalHtml", revision.getCanonicalSnapshotContent(),
                "snapshotHash", revision.getSnapshotHash() != null ? revision.getSnapshotHash() : "",
                "status", revision.getStatus().name()
        );
    }

    @Override
    @Transactional
    public void requestChanges(Long contractId, Long revisionId, ChangeRequestDto request, Long internUserId) {
        ContractRevision revision = validateInternOwnership(contractId, revisionId, internUserId);
        Contract contract = revision.getContract();

        if (revision.getStatus() != DynamicContractStatus.SENT) {
            throw new IllegalStateException("Chỉ có thể yêu cầu chỉnh sửa khi hợp đồng ở trạng thái SENT");
        }

        revision.setStatus(DynamicContractStatus.CHANGES_REQUESTED);
        revision.setChangeRequestReason(request.getReason());
        contract.setStatus(DynamicContractStatus.CHANGES_REQUESTED);

        revisionRepository.save(revision);
        contractRepository.save(contract);

        logAudit(contract.getId(), revision.getId(), internUserId, "INTERN", "CHANGE_REQUESTED", Map.of("reason", request.getReason()));
    }

    @Override
    @Transactional
    public void confirmRevision(Long contractId, Long revisionId, ConfirmRevisionRequest request, Long internUserId) {
        ContractRevision revision = validateInternOwnership(contractId, revisionId, internUserId);
        Contract contract = revision.getContract();

        if (revision.getStatus() != DynamicContractStatus.SENT) {
            throw new IllegalStateException("Chỉ có thể xác nhận thông tin khi hợp đồng ở trạng thái SENT");
        }

        revision.setStatus(DynamicContractStatus.INTERN_CONFIRMED);
        revision.setConsentTextVersion(request.getConsentTextVersion());
        revision.setConsentTextSnapshot(request.getConsentTextSnapshot());
        revision.setConfirmedAt(LocalDateTime.now());

        contract.setStatus(DynamicContractStatus.INTERN_CONFIRMED);

        revisionRepository.save(revision);
        contractRepository.save(contract);

        logAudit(contract.getId(), revision.getId(), internUserId, "INTERN", "CONFIRMED", Map.of("consentVersion", request.getConsentTextVersion()));
    }

    @Override
    @Transactional
    public void signRevision(Long contractId, Long revisionId, SignContractRequest request, Long internUserId, String ipAddress, String userAgent) {
        ContractRevision revision = validateInternOwnership(contractId, revisionId, internUserId);
        Contract contract = revision.getContract();

        if (revision.getStatus() != DynamicContractStatus.INTERN_CONFIRMED && revision.getStatus() != DynamicContractStatus.SENT) {
            throw new IllegalStateException("Bạn phải kiểm tra và xác nhận thông tin trước khi thực hiện ký hợp đồng");
        }

        if (signatureRepository.existsByContractRevisionId(revision.getId())) {
            throw new IllegalStateException("Hợp đồng này đã được ký, không thể ký lại");
        }

        String signatureHash = sanitizerAndValidator.calculateSha256(request.getSignatureData());

        ContractSignature signature = ContractSignature.builder()
                .contractRevision(revision)
                .signerUserId(internUserId)
                .signatureData(request.getSignatureData())
                .signedAt(LocalDateTime.now())
                .ipAddress(ipAddress != null ? ipAddress : "127.0.0.1")
                .userAgent(userAgent != null ? userAgent : "Unknown")
                .authMethod(request.getAuthMethod())
                .documentHash(revision.getSnapshotHash())
                .signatureHash(signatureHash)
                .consentTextVersion(revision.getConsentTextVersion() != null ? revision.getConsentTextVersion() : "v1.0")
                .consentTextSnapshot(revision.getConsentTextSnapshot() != null ? revision.getConsentTextSnapshot() : "Cam kết đầy đủ")
                .build();
        signatureRepository.save(signature);

        revision.setStatus(DynamicContractStatus.SIGNED);
        revision.setSignedAt(LocalDateTime.now());

        contract.setStatus(DynamicContractStatus.SIGNED);

        // --- BƯỚC 15 & PHƯƠNG ÁN 1: SERVER-SIDE PDF RENDERING & S3 STORAGE ---
        try {
            log.info("Bắt đầu sinh PDF hợp đồng có chữ ký số cho contract: {}, revision: {}", contractId, revisionId);
            byte[] pdfBytes = pdfRendererService.renderContractPdf(
                    revision.getCanonicalSnapshotContent(),
                    request.getSignatureData(),
                    contract.getInternProfile().getFullName()
            );

            // Tính mã băm toàn vẹn SHA-256 của file PDF
            String pdfHash = sanitizerAndValidator.calculateSha256(new String(pdfBytes, java.nio.charset.StandardCharsets.ISO_8859_1));
            // Hoặc tính SHA-256 trực tiếp từ byte[]
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(pdfBytes);
                pdfHash = java.util.HexFormat.of().formatHex(hash);
            } catch (Exception ignored) {}

            revision.setPdfHash(pdfHash);

            // Đường dẫn lưu trữ S3: contracts/{internCode}/{contractNumber}_rev{revNumber}.pdf
            String s3Key = String.format("contracts/%s/%s_rev%d.pdf",
                    contract.getInternProfile().getInternCode(),
                    contract.getContractNumber(),
                    revision.getRevisionNumber());

            // Lưu trực tiếp lên Object Storage
            String finalS3Key = fileServiceClient.uploadBytesToS3(pdfBytes, s3Key, "application/pdf");
            revision.setPdfStorageKey(finalS3Key != null ? finalS3Key : s3Key);
            log.info("Lưu trữ PDF hợp đồng lên S3 thành công với key: {}, sha256: {}", revision.getPdfStorageKey(), pdfHash);
        } catch (Exception e) {
            log.error("Cảnh báo: Sinh hoặc lưu trữ PDF hợp đồng lên S3 thất bại: {}", e.getMessage(), e);
            // Tiếp tục lưu DB để không chặn luồng ký nếu storage tạm thời gặp lỗi kết nối
        }

        revisionRepository.save(revision);
        contractRepository.save(contract);

        logAudit(contract.getId(), revision.getId(), internUserId, "INTERN", "SIGNED", 
                Map.of("signatureHash", signatureHash, "pdfHash", revision.getPdfHash() != null ? revision.getPdfHash() : ""));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getContractPdfBytes(Long contractId, Long revisionId, Long internUserId) {
        ContractRevision revision = validateInternOwnership(contractId, revisionId, internUserId);
        Contract contract = revision.getContract();

        // 1. Nếu đã có chữ ký, kết xuất PDF đầy đủ chữ ký
        String signatureData = revision.getSignature() != null ? revision.getSignature().getSignatureData() : null;
        return pdfRendererService.renderContractPdf(
                revision.getCanonicalSnapshotContent(),
                signatureData,
                contract.getInternProfile().getFullName()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getMyContractPdfBytes(Long internUserId) {
        InternProfile internProfile = internProfileRepository.findByUserId(internUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh của bạn"));

        Contract contract = contractRepository.findFirstByInternProfileIdOrderByCreatedAtDesc(internProfile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hiện tại bạn chưa có hợp đồng thực tập nào"));

        Long currentRevId = contract.getCurrentRevisionId();
        if (currentRevId == null) {
            throw new ResourceNotFoundException("Không tìm thấy phiên bản hợp đồng khả dụng");
        }

        ContractRevision revision = revisionRepository.findById(currentRevId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản sửa đổi hợp đồng hiện tại"));

        String signatureData = revision.getSignature() != null ? revision.getSignature().getSignatureData() : null;
        return pdfRendererService.renderContractPdf(
                revision.getCanonicalSnapshotContent(),
                signatureData,
                contract.getInternProfile().getFullName()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String getContractPdfViewUrl(Long contractId, Long revisionId, Long internUserId) {
        ContractRevision revision = validateInternOwnership(contractId, revisionId, internUserId);
        if (revision.getPdfStorageKey() != null && !revision.getPdfStorageKey().isBlank()) {
            try {
                var presignedRes = fileServiceClient.createPresignedView(
                        org.example.internservice.intern.client.FileServiceClient.PresignedViewRequest.builder()
                                .fileKey(revision.getPdfStorageKey())
                                .expiresInMinutes(30)
                                .build()
                );
                return presignedRes.getPresignedUrl();
            } catch (Exception e) {
                log.warn("Không thể lấy Presigned S3 View URL, fallback tải trực tiếp: {}", e.getMessage());
            }
        }
        return null;
    }

    @Override
    @Transactional
    public DynamicContractResponse createRevision(Long contractId, CreateContractDraftRequest request, String hrUsername) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + contractId));

        int nextRevisionNumber = contract.getRevisions().size() + 1;

        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu hợp đồng: " + request.getTemplateId()));

        ContractTemplateVersion templateVersion = template.getVersions().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Mẫu hợp đồng chưa có phiên bản"));

        InternProfile internProfile = contract.getInternProfile();
        Map<String, String> variables = buildVariablesMap(internProfile, contract.getContractNumber(), request);
        String canonicalHtml = sanitizerAndValidator.renderCanonicalSnapshot(templateVersion.getContentTemplate(), variables);
        String snapshotHash = sanitizerAndValidator.calculateSha256(canonicalHtml);

        ContractRevision newRevision = ContractRevision.builder()
                .contract(contract)
                .revisionNumber(nextRevisionNumber)
                .templateVersion(templateVersion)
                .variablesPayload(writeJson(variables))
                .canonicalSnapshotContent(canonicalHtml)
                .snapshotHash(snapshotHash)
                .status(DynamicContractStatus.DRAFT)
                .createdBy(hrUsername)
                .build();
        newRevision = revisionRepository.save(newRevision);

        contract.setCurrentRevisionId(newRevision.getId());
        contract.setStatus(DynamicContractStatus.HR_REVISING);
        contractRepository.save(contract);

        logAudit(contract.getId(), newRevision.getId(), 0L, "HR", "REVISION_CREATED", Map.of("revisionNumber", nextRevisionNumber));

        return toResponse(contract, newRevision);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DynamicContractResponse> getAllContracts() {
        log.info("HR truy vấn danh sách toàn bộ hợp đồng điện tử động");
        List<Contract> contracts = contractRepository.findAllByOrderByCreatedAtDesc();
        return contracts.stream().map(c -> toResponse(c, null)).toList();
    }

    private ContractRevision validateInternOwnership(Long contractId, Long revisionId, Long internUserId) {
        InternProfile internProfile = internProfileRepository.findByUserId(internUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh của bạn"));

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng: " + contractId));

        if (!contract.getInternProfile().getId().equals(internProfile.getId())) {
            throw new SecurityException("Bạn không có quyền truy cập vào hợp đồng này");
        }

        return revisionRepository.findById(revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản sửa đổi hợp đồng: " + revisionId));
    }

    private Map<String, String> buildVariablesMap(InternProfile intern, String contractNumber, CreateContractDraftRequest req) {
        Map<String, String> map = new HashMap<>();
        map.put("intern.fullName", intern.getFullName() != null ? intern.getFullName() : "");
        map.put("intern.cccd", intern.getInternCode() != null ? intern.getInternCode() : "");
        map.put("intern.university", intern.getUniversity() != null ? intern.getUniversity() : "");
        map.put("intern.email", intern.getEmail() != null ? intern.getEmail() : "");
        map.put("intern.phone", intern.getPhone() != null ? intern.getPhone() : "");
        map.put("contract.number", contractNumber);
        String allowanceStr = "0 VNĐ / tháng";
        if (req.getAllowanceAmount() != null) {
            java.text.NumberFormat nf = java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN"));
            allowanceStr = nf.format(req.getAllowanceAmount()) + " VNĐ / tháng";
        }
        map.put("contract.allowance", allowanceStr);
        map.put("contract.startDate", req.getStartDate() != null ? req.getStartDate().toString() : "");
        map.put("contract.endDate", req.getEndDate() != null ? req.getEndDate().toString() : "");
        map.put("contract.position", req.getPosition() != null ? req.getPosition() : "Thực tập sinh");
        map.put("contract.department", req.getDepartment() != null ? req.getDepartment() : "Công nghệ thông tin");
        map.put("contract.supervisor", req.getSupervisorName() != null ? req.getSupervisorName() : "");
        map.put("contract.customTerms", req.getCustomTerms() != null ? req.getCustomTerms() : "");
        return map;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void logAudit(Long contractId, Long revisionId, Long actorUserId, String actorRole, String action, Map<String, Object> metadata) {
        ContractAuditLog auditLog = ContractAuditLog.builder()
                .contractId(contractId)
                .contractRevisionId(revisionId)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .action(action)
                .timestamp(LocalDateTime.now())
                .metadata(writeJson(metadata))
                .build();
        auditLogRepository.save(auditLog);
    }

    private DynamicContractResponse toResponse(Contract contract, ContractRevision revision) {
        return DynamicContractResponse.builder()
                .id(contract.getId())
                .contractNumber(contract.getContractNumber())
                .internId(contract.getInternProfile().getId())
                .internCode(contract.getInternProfile().getInternCode())
                .internFullName(contract.getInternProfile().getFullName())
                .internEmail(contract.getInternProfile().getEmail())
                .programId(contract.getInternshipProgram().getId())
                .programName(contract.getInternshipProgram().getName())
                .status(contract.getStatus())
                .currentRevisionId(revision != null ? revision.getId() : contract.getCurrentRevisionId())
                .currentRevisionNumber(revision != null ? revision.getRevisionNumber() : 1)
                .snapshotHash(revision != null ? revision.getSnapshotHash() : "")
                .effectiveFrom(contract.getEffectiveFrom())
                .effectiveTo(contract.getEffectiveTo())
                .allowanceAmount(contract.getAllowanceAmount())
                .createdBy(contract.getCreatedBy())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }
}
