package org.example.internservice.intern.service;

import org.example.internservice.common.storage.FileStorageService;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.dto.request.ConfirmContractRequest;
import org.example.internservice.intern.dto.request.RejectContractRequest;
import org.example.internservice.intern.dto.request.UploadContractRequest;
import org.example.internservice.intern.dto.response.ContractResponse;
import org.example.internservice.intern.dto.response.DocumentDownloadDto;
import org.example.internservice.intern.entity.InternContract;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.ContractStatus;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternContractRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.service.impl.InternContractServiceImpl;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternContractServiceTest {

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternContractRepository internContractRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private org.example.internservice.intern.client.IdentityServiceClient identityServiceClient;

    @Mock
    private org.example.internservice.intern.client.NotificationEventDispatcher notificationEventDispatcher;

    @InjectMocks
    private InternContractServiceImpl internContractService;

    private InternProfile mockApprovedIntern;
    private InternProfile mockPendingIntern;
    private UploadContractRequest validRequest;
    private MockMultipartFile validPdfFile;
    private CustomUserDetails internUser;
    private CustomUserDetails otherInternUser;
    private CustomUserDetails hrUser;

    @BeforeEach
    void setUp() {
        mockApprovedIntern = InternProfile.builder()
                .internCode("INT-202609-0001")
                .fullName("Nguyễn Văn A")
                .email("intern.a@example.com")
                .status(InternStatus.APPROVED)
                .build();
        mockApprovedIntern.setId(1L);
        mockApprovedIntern.setUserId(100L);

        mockPendingIntern = InternProfile.builder()
                .internCode("INT-202609-0002")
                .fullName("Trần Thị B")
                .email("intern.b@example.com")
                .status(InternStatus.PENDING)
                .build();
        mockPendingIntern.setId(2L);
        mockPendingIntern.setUserId(200L);

        validRequest = UploadContractRequest.builder()
                .contractTitle("Hợp đồng thực tập tốt nghiệp")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .contractNumber("HDTT-202609-0001")
                .allowanceAmount(BigDecimal.valueOf(3000000))
                .notes("Hợp đồng đợt 2")
                .build();

        validPdfFile = new MockMultipartFile(
                "file",
                "hop_dong_thuc_tap.pdf",
                "application/pdf",
                "Mock PDF content".getBytes()
        );

        internUser = CustomUserDetails.builder()
                .userId(100L)
                .username("intern.a@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_INTERN")))
                .build();

        otherInternUser = CustomUserDetails.builder()
                .userId(300L)
                .username("intern.other@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_INTERN")))
                .build();

        hrUser = CustomUserDetails.builder()
                .userId(1L)
                .username("hr_manager")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_HR")))
                .build();
    }

    @Test
    @DisplayName("UT-BE-01: Tải hợp đồng PDF hợp lệ cho thực tập sinh APPROVED -> Thành công, status PENDING_SIGNATURE")
    void uploadContract_validApprovedIntern_shouldSucceed() {
        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockApprovedIntern));
        when(internContractRepository.existsByContractNumber("HDTT-202609-0001"))
                .thenReturn(false);
        when(fileStorageService.storeFile(eq(validPdfFile), eq("contracts/INT-202609-0001")))
                .thenReturn("uuid-contract.pdf");

        InternContract savedEntity = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng thực tập tốt nghiệp")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .allowanceAmount(BigDecimal.valueOf(3000000))
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName("hop_dong_thuc_tap.pdf")
                .fileName("uuid-contract.pdf")
                .filePath("contracts/INT-202609-0001/uuid-contract.pdf")
                .fileSize((long) "Mock PDF content".length())
                .contentType("application/pdf")
                .uploadedBy("hr_manager")
                .build();
        savedEntity.setId(10L);
        savedEntity.setCreatedAt(LocalDateTime.now());
        savedEntity.setUpdatedAt(LocalDateTime.now());

        when(internContractRepository.save(any(InternContract.class)))
                .thenReturn(savedEntity);

        ContractResponse response = internContractService.uploadContract(
                "INT-202609-0001", validPdfFile, validRequest, "hr_manager"
        );

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("INT-202609-0001", response.getInternCode());
        assertEquals("HDTT-202609-0001", response.getContractNumber());
        assertEquals(ContractStatus.PENDING_SIGNATURE, response.getStatus());
        assertEquals("hr_manager", response.getUploadedBy());
        verify(internContractRepository, times(1)).save(any(InternContract.class));
    }

    @Test
    @DisplayName("UT-BE-02: Tải hợp đồng cho thực tập sinh PENDING -> Ném BadRequestException")
    void uploadContract_pendingIntern_shouldThrowBadRequestException() {
        when(internProfileRepository.findByInternCode("INT-202609-0002"))
                .thenReturn(Optional.of(mockPendingIntern));

        assertThrows(BadRequestException.class, () ->
                internContractService.uploadContract("INT-202609-0002", validPdfFile, validRequest, "hr")
        );
    }

    @Test
    @DisplayName("UT-BE-03: Tải hợp đồng với tệp tin rỗng -> Ném BadRequestException")
    void uploadContract_emptyFile_shouldThrowBadRequestException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(BadRequestException.class, () ->
                internContractService.uploadContract("INT-202609-0001", emptyFile, validRequest, "hr")
        );
    }

    @Test
    @DisplayName("UT-BE-04: Tải tệp tin sai định dạng (.exe) -> Ném BadRequestException")
    void uploadContract_invalidExtension_shouldThrowBadRequestException() {
        MockMultipartFile exeFile = new MockMultipartFile("file", "app.exe", "application/octet-stream", "dummy".getBytes());

        assertThrows(BadRequestException.class, () ->
                internContractService.uploadContract("INT-202609-0001", exeFile, validRequest, "hr")
        );
    }

    @Test
    @DisplayName("UT-BE-05: Ngày kết thúc trước ngày bắt đầu -> Ném BadRequestException")
    void uploadContract_invalidDates_shouldThrowBadRequestException() {
        UploadContractRequest badDateRequest = UploadContractRequest.builder()
                .contractTitle("Hợp đồng")
                .startDate(LocalDate.of(2026, 12, 31))
                .endDate(LocalDate.of(2026, 10, 1))
                .build();

        assertThrows(BadRequestException.class, () ->
                internContractService.uploadContract("INT-202609-0001", validPdfFile, badDateRequest, "hr")
        );
    }

    @Test
    @DisplayName("UT-BE-06: Trùng mã hợp đồng thủ công -> Ném DuplicateResourceException")
    void uploadContract_duplicateProvidedContractNumber_shouldThrowDuplicateException() {
        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockApprovedIntern));
        when(internContractRepository.existsByContractNumber("HDTT-202609-0001"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class, () ->
                internContractService.uploadContract("INT-202609-0001", validPdfFile, validRequest, "hr")
        );
    }

    @Test
    @DisplayName("UT-BE-07: Tự động sinh mã hợp đồng khi không cung cấp -> Định dạng HDTT-yyyyMM-0001")
    void uploadContract_autoGenerateContractNumber_shouldGenerateCorrectPattern() {
        UploadContractRequest requestNoNumber = UploadContractRequest.builder()
                .contractTitle("Hợp đồng tự sinh mã")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();

        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockApprovedIntern));
        when(internContractRepository.findContractNumbersByPrefix(anyString()))
                .thenReturn(Collections.emptyList());
        when(internContractRepository.existsByContractNumber(anyString()))
                .thenReturn(false);
        when(fileStorageService.storeFile(any(), anyString()))
                .thenReturn("uuid.pdf");

        InternContract saved = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng tự sinh mã")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName("hop_dong_thuc_tap.pdf")
                .fileName("uuid.pdf")
                .filePath("path")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr")
                .build();
        saved.setId(1L);

        when(internContractRepository.save(any(InternContract.class))).thenReturn(saved);

        ContractResponse response = internContractService.uploadContract("INT-202609-0001", validPdfFile, requestNoNumber, "hr");
        assertNotNull(response);
        assertTrue(response.getContractNumber().startsWith("HDTT-"));
    }

    @Test
    @DisplayName("UT-BE-08: Lấy danh sách hợp đồng theo internCode -> Trả về danh sách")
    void getContractsByInternCode_validCode_shouldReturnList() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName("contract.pdf")
                .fileName("uuid.pdf")
                .filePath("path")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr")
                .build();
        contract.setId(1L);

        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockApprovedIntern));
        when(internContractRepository.findByInternCodeWithProfile("INT-202609-0001"))
                .thenReturn(List.of(contract));

        List<ContractResponse> list = internContractService.getContractsByInternCode("INT-202609-0001");

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("HDTT-202609-0001", list.get(0).getContractNumber());
    }

    @Test
    @DisplayName("UT-BE-09: Tải tệp hợp đồng download hợp lệ -> Trả về DocumentDownloadDto")
    void loadContractForDownload_validId_shouldReturnDto() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .originalFileName("hop_dong.pdf")
                .filePath("contracts/INT-202609-0001/uuid.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .build();
        contract.setId(1L);

        Resource mockResource = new ByteArrayResource("PDF data".getBytes());

        when(internContractRepository.findByIdWithProfile(1L))
                .thenReturn(Optional.of(contract));
        when(fileStorageService.loadFileAsResource("contracts/INT-202609-0001/uuid.pdf"))
                .thenReturn(mockResource);

        DocumentDownloadDto dto = internContractService.loadContractForDownload(1L);

        assertNotNull(dto);
        assertEquals("hop_dong.pdf", dto.getOriginalFileName());
        assertEquals("application/pdf", dto.getContentType());
        assertEquals(1024L, dto.getFileSize());
        assertNotNull(dto.getResource());
    }

    @Test
    @DisplayName("UT-BE-10: Tải tệp hợp đồng với ID không tồn tại -> Ném ResourceNotFoundException")
    void loadContractForDownload_notFoundId_shouldThrowException() {
        when(internContractRepository.findByIdWithProfile(999L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                internContractService.loadContractForDownload(999L)
        );
    }

    // ==========================================
    // TM-14: Test cases cho xác nhận / từ chối hợp đồng
    // ==========================================

    @Test
    @DisplayName("UT-BE-11: Xác nhận ký hợp đồng hợp lệ -> Chuyển SIGNED và cập nhật hồ sơ sang INTERNING")
    void confirmContract_validRequest_shouldSignSuccessfullyAndSetInterning() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng thực tập")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName("contract.pdf")
                .fileName("uuid.pdf")
                .filePath("path")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr")
                .build();
        contract.setId(1L);

        when(internContractRepository.findByIdWithProfile(1L))
                .thenReturn(Optional.of(contract));
        when(internContractRepository.save(any(InternContract.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConfirmContractRequest request = ConfirmContractRequest.builder()
                .agreeTerms(true)
                .signerFullName("Nguyễn Văn A")
                .confirmationNote("Em đồng ý với các điều khoản")
                .build();

        ContractResponse response = internContractService.confirmContract(1L, request, internUser);

        assertNotNull(response);
        assertEquals(ContractStatus.ACTIVE, response.getStatus());
        assertEquals("Nguyễn Văn A", response.getSignerFullName());
        assertNotNull(response.getSignedAt());
        assertEquals(InternStatus.INTERNING, response.getInternProfileStatus());
        verify(internProfileRepository, times(1)).save(mockApprovedIntern);
        assertEquals(InternStatus.INTERNING, mockApprovedIntern.getStatus());
    }

    @Test
    @DisplayName("UT-BE-12: Chặn IDOR - Cố ý ký hợp đồng của người khác -> Ném AccessDeniedException")
    void confirmContract_otherUserContract_shouldThrowAccessDeniedException() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .status(ContractStatus.PENDING_SIGNATURE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .build();
        contract.setId(1L);

        when(internContractRepository.findByIdWithProfile(1L))
                .thenReturn(Optional.of(contract));

        ConfirmContractRequest request = ConfirmContractRequest.builder()
                .agreeTerms(true)
                .signerFullName("Kẻ Giả Mạo")
                .build();

        assertThrows(AccessDeniedException.class, () ->
                internContractService.confirmContract(1L, request, otherInternUser)
        );
    }

    @Test
    @DisplayName("UT-BE-13: Ký hợp đồng đã ở trạng thái SIGNED -> Ném BadRequestException")
    void confirmContract_alreadySignedContract_shouldThrowBadRequestException() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .status(ContractStatus.SIGNED)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .build();
        contract.setId(1L);

        when(internContractRepository.findByIdWithProfile(1L))
                .thenReturn(Optional.of(contract));

        ConfirmContractRequest request = ConfirmContractRequest.builder()
                .agreeTerms(true)
                .signerFullName("Nguyễn Văn A")
                .build();

        assertThrows(BadRequestException.class, () ->
                internContractService.confirmContract(1L, request, internUser)
        );
    }

    @Test
    @DisplayName("UT-BE-14: Ký hợp đồng đã quá hạn endDate -> Chuyển EXPIRED và ném BadRequestException")
    void confirmContract_expiredContract_shouldTransitionToExpiredAndThrowBadRequestException() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .status(ContractStatus.PENDING_SIGNATURE)
                .startDate(LocalDate.now().minusMonths(4))
                .endDate(LocalDate.now().minusDays(1))
                .build();
        contract.setId(1L);

        when(internContractRepository.findByIdWithProfile(1L))
                .thenReturn(Optional.of(contract));

        ConfirmContractRequest request = ConfirmContractRequest.builder()
                .agreeTerms(true)
                .signerFullName("Nguyễn Văn A")
                .build();

        assertThrows(BadRequestException.class, () ->
                internContractService.confirmContract(1L, request, internUser)
        );

        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
        verify(internContractRepository, times(1)).save(contract);
    }

    @Test
    @DisplayName("UT-BE-15: Ký hợp đồng không tồn tại -> Ném ResourceNotFoundException")
    void confirmContract_notFoundContractId_shouldThrowResourceNotFoundException() {
        when(internContractRepository.findByIdWithProfile(999L))
                .thenReturn(Optional.empty());

        ConfirmContractRequest request = ConfirmContractRequest.builder()
                .agreeTerms(true)
                .signerFullName("Nguyễn Văn A")
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                internContractService.confirmContract(999L, request, internUser)
        );
    }

    @Test
    @DisplayName("UT-BE-16: Từ chối hợp đồng kèm lý do hợp lệ -> Chuyển sang REJECTED_BY_INTERN")
    void rejectContract_validReason_shouldRejectSuccessfully() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng thực tập")
                .status(ContractStatus.PENDING_SIGNATURE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .originalFileName("contract.pdf")
                .fileName("uuid.pdf")
                .filePath("path")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr")
                .build();
        contract.setId(1L);

        when(internContractRepository.findByIdWithProfile(1L))
                .thenReturn(Optional.of(contract));
        when(internContractRepository.save(any(InternContract.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RejectContractRequest request = RejectContractRequest.builder()
                .rejectionReason("Do lịch thi tốt nghiệp tại trường bị thay đổi nên em không thể tham gia")
                .build();

        ContractResponse response = internContractService.rejectContract(1L, request, internUser);

        assertNotNull(response);
        assertEquals(ContractStatus.REJECTED_BY_INTERN, response.getStatus());
        assertEquals("Do lịch thi tốt nghiệp tại trường bị thay đổi nên em không thể tham gia", response.getRejectionReason());
        verify(internContractRepository, times(1)).save(contract);
    }

    @Test
    @DisplayName("UT-BE-17: Chặn IDOR - Cố ý từ chối hợp đồng của người khác -> Ném AccessDeniedException")
    void rejectContract_otherUserContract_shouldThrowAccessDeniedException() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .status(ContractStatus.PENDING_SIGNATURE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .build();
        contract.setId(1L);

        when(internContractRepository.findByIdWithProfile(1L))
                .thenReturn(Optional.of(contract));

        RejectContractRequest request = RejectContractRequest.builder()
                .rejectionReason("Lý do từ chối từ người ngoài")
                .build();

        assertThrows(AccessDeniedException.class, () ->
                internContractService.rejectContract(1L, request, otherInternUser)
        );
    }

    @Test
    @DisplayName("UT-BE-18: Lấy danh sách hợp đồng cá nhân của thực tập sinh -> Chỉ trả về hợp đồng của chính mình")
    void getMyContracts_validIntern_shouldReturnOnlyOwnContracts() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng cá nhân")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName("contract.pdf")
                .fileName("uuid.pdf")
                .filePath("path")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr")
                .build();
        contract.setId(1L);

        when(internContractRepository.findAllByUserIdOrEmailWithProfile(100L, "intern.a@example.com"))
                .thenReturn(List.of(contract));

        List<ContractResponse> myContracts = internContractService.getMyContracts(internUser);

        assertNotNull(myContracts);
        assertEquals(1, myContracts.size());
        assertEquals("HDTT-202609-0001", myContracts.get(0).getContractNumber());
    }

    @Test
    @DisplayName("UT-BE-19: Xem chi tiết hợp đồng của chính mình -> Thành công")
    void getContractById_asInternOwner_shouldReturnContractDetails() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng chi tiết")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName("contract.pdf")
                .fileName("uuid.pdf")
                .filePath("path")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr")
                .build();
        contract.setId(1L);

        when(internContractRepository.findByIdWithProfile(1L))
                .thenReturn(Optional.of(contract));

        ContractResponse response = internContractService.getContractById(1L, internUser);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("HDTT-202609-0001", response.getContractNumber());
    }

    @Test
    @DisplayName("UT-BE-20: Xem chi tiết hợp đồng với tư cách HR -> Thành công mà không bị chặn IDOR")
    void getContractById_asHr_shouldReturnContractDetails() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng chi tiết")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName("contract.pdf")
                .fileName("uuid.pdf")
                .filePath("path")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr")
                .build();
        contract.setId(1L);

        when(internContractRepository.findByIdWithProfile(1L))
                .thenReturn(Optional.of(contract));

        ContractResponse response = internContractService.getContractById(1L, hrUser);

        assertNotNull(response);
        assertEquals(1L, response.getId());
    }

    @Test
    @DisplayName("UT-BE-21: Lấy hợp đồng active gần nhất -> Trả về hợp đồng PENDING_SIGNATURE hoặc ACTIVE/SIGNED")
    void getMyActiveContract_shouldReturnPendingOrActiveContract() {
        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng thực tập")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(ContractStatus.ACTIVE)
                .originalFileName("contract.pdf")
                .fileName("uuid.pdf")
                .filePath("path")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr")
                .build();
        contract.setId(1L);

        when(internContractRepository.findAllByUserIdOrEmailWithProfile(100L, "intern.a@example.com"))
                .thenReturn(List.of(contract));

        ContractResponse response = internContractService.getMyActiveContract(internUser);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(ContractStatus.ACTIVE, response.getStatus());
    }

    @Test
    @DisplayName("UT-BE-22: Từ chối ký hợp đồng với lý do dưới 10 ký tự -> Ném BadRequestException")
    void rejectContract_reasonLessThan10Chars_shouldThrowBadRequestException() {
        RejectContractRequest request = RejectContractRequest.builder()
                .rejectionReason("Ngắn quá")
                .build();

        assertThrows(BadRequestException.class, () ->
                internContractService.rejectContract(1L, request, internUser)
        );
    }
}
