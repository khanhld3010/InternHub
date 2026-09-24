package org.example.internservice.intern.service;

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
import org.example.internservice.intern.service.impl.InternContractServiceImpl;
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

    @InjectMocks
    private InternContractServiceImpl internContractService;

    private InternProfile mockApprovedIntern;
    private InternProfile mockPendingIntern;
    private UploadContractRequest validRequest;
    private MockMultipartFile validPdfFile;

    @BeforeEach
    void setUp() {
        mockApprovedIntern = InternProfile.builder()
                .internCode("INT-202609-0001")
                .fullName("Nguyễn Văn A")
                .status(InternStatus.APPROVED)
                .build();

        mockPendingIntern = InternProfile.builder()
                .internCode("INT-202609-0002")
                .fullName("Trần Thị B")
                .status(InternStatus.PENDING)
                .build();

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

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                internContractService.uploadContract("INT-202609-0002", validPdfFile, validRequest, "hr_manager")
        );

        assertTrue(ex.getMessage().contains("APPROVED"));
        verify(fileStorageService, never()).storeFile(any(), any());
        verify(internContractRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-BE-03: Tải hợp đồng khi ngày kết thúc <= ngày bắt đầu -> Ném BadRequestException")
    void uploadContract_invalidDateRange_shouldThrowBadRequestException() {
        validRequest.setEndDate(validRequest.getStartDate().minusDays(1));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                internContractService.uploadContract("INT-202609-0001", validPdfFile, validRequest, "hr_manager")
        );

        assertTrue(ex.getMessage().contains("sau ngày bắt đầu"));
        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    @DisplayName("UT-BE-04: Tải hợp đồng với contractNumber bị trùng -> Ném DuplicateResourceException")
    void uploadContract_duplicateContractNumber_shouldThrowDuplicateResourceException() {
        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockApprovedIntern));
        when(internContractRepository.existsByContractNumber("HDTT-202609-0001"))
                .thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () ->
                internContractService.uploadContract("INT-202609-0001", validPdfFile, validRequest, "hr_manager")
        );

        assertTrue(ex.getMessage().contains("đã tồn tại"));
        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    @DisplayName("UT-BE-05: Tải file không hợp lệ (.exe) -> Ném BadRequestException")
    void uploadContract_invalidFileType_shouldThrowBadRequestException() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file",
                "trojan.exe",
                "application/octet-stream",
                "dummy exe content".getBytes()
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                internContractService.uploadContract("INT-202609-0001", exeFile, validRequest, "hr_manager")
        );

        assertTrue(ex.getMessage().contains("Định dạng tệp tin không hợp lệ"));
    }

    @Test
    @DisplayName("UT-BE-06: DB save gặp lỗi -> Tự động Rollback xóa file vật lý")
    void uploadContract_databaseFailure_shouldRollbackPhysicalFile() {
        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockApprovedIntern));
        when(internContractRepository.existsByContractNumber("HDTT-202609-0001"))
                .thenReturn(false);
        when(fileStorageService.storeFile(eq(validPdfFile), eq("contracts/INT-202609-0001")))
                .thenReturn("uuid-contract.pdf");
        when(internContractRepository.save(any(InternContract.class)))
                .thenThrow(new RuntimeException("Database connection dead"));

        assertThrows(RuntimeException.class, () ->
                internContractService.uploadContract("INT-202609-0001", validPdfFile, validRequest, "hr_manager")
        );

        verify(fileStorageService, times(1))
                .deleteFile("contracts/INT-202609-0001/uuid-contract.pdf");
    }

    @Test
    @DisplayName("UT-BE-07: Không nhập contractNumber -> Tự động sinh mã hợp đồng HDTT-YYYYMM-XXXX")
    void uploadContract_noContractNumber_shouldAutoGenerate() {
        validRequest.setContractNumber(null);

        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockApprovedIntern));
        when(internContractRepository.findContractNumbersByPrefix(anyString()))
                .thenReturn(Collections.emptyList());
        when(internContractRepository.existsByContractNumber(anyString()))
                .thenReturn(false);
        when(fileStorageService.storeFile(eq(validPdfFile), eq("contracts/INT-202609-0001")))
                .thenReturn("uuid-contract.pdf");

        InternContract saved = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng thực tập")
                .startDate(validRequest.getStartDate())
                .endDate(validRequest.getEndDate())
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName("hop_dong.pdf")
                .fileName("uuid.pdf")
                .filePath("contracts/INT-202609-0001/uuid.pdf")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr_manager")
                .build();
        saved.setId(15L);

        when(internContractRepository.save(any(InternContract.class))).thenReturn(saved);

        ContractResponse response = internContractService.uploadContract(
                "INT-202609-0001", validPdfFile, validRequest, "hr_manager"
        );

        assertNotNull(response);
        assertNotNull(response.getContractNumber());
        assertTrue(response.getContractNumber().startsWith("HDTT-"));
    }

    @Test
    @DisplayName("UT-BE-08: Tra cứu danh sách hợp đồng theo internCode -> Thành công")
    void getContractsByInternCode_valid_shouldReturnList() {
        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockApprovedIntern));

        InternContract contract = InternContract.builder()
                .internProfile(mockApprovedIntern)
                .contractNumber("HDTT-202609-0001")
                .contractTitle("Hợp đồng thực tập")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(ContractStatus.PENDING_SIGNATURE)
                .originalFileName("hop_dong.pdf")
                .fileName("uuid.pdf")
                .filePath("contracts/INT-202609-0001/uuid.pdf")
                .fileSize(100L)
                .contentType("application/pdf")
                .uploadedBy("hr_manager")
                .build();
        contract.setId(1L);

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
}
