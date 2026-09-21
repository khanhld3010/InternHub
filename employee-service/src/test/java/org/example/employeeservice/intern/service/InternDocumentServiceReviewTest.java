package org.example.employeeservice.intern.service;

import org.example.employeeservice.common.storage.FileStorageService;
import org.example.employeeservice.exception.BadRequestException;
import org.example.employeeservice.exception.ResourceNotFoundException;
import org.example.employeeservice.intern.dto.request.ReviewDocumentRequest;
import org.example.employeeservice.intern.dto.response.DocumentDownloadDto;
import org.example.employeeservice.intern.dto.response.DocumentResponse;
import org.example.employeeservice.intern.entity.InternDocument;
import org.example.employeeservice.intern.entity.InternProfile;
import org.example.employeeservice.intern.entity.enums.DocumentStatus;
import org.example.employeeservice.intern.entity.enums.DocumentType;
import org.example.employeeservice.intern.repository.InternDocumentRepository;
import org.example.employeeservice.intern.repository.InternProfileRepository;
import org.example.employeeservice.intern.service.impl.InternDocumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternDocumentServiceReviewTest {

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternDocumentRepository internDocumentRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private InternDocumentServiceImpl internDocumentService;

    private InternProfile mockInternProfile;
    private InternDocument mockDocument;

    @BeforeEach
    void setUp() {
        mockInternProfile = InternProfile.builder()
                .internCode("INT-202609-0001")
                .fullName("Nguyễn Văn A")
                .status(org.example.employeeservice.intern.entity.enums.InternStatus.PENDING)
                .build();

        mockDocument = InternDocument.builder()
                .internProfile(mockInternProfile)
                .documentType(DocumentType.CV)
                .originalFileName("NguyenVanA_CV.pdf")
                .fileName("uuid-cv.pdf")
                .filePath("interns/INT-202609-0001/uuid-cv.pdf")
                .fileSize(1024L)
                .contentType("application/pdf")
                .status(DocumentStatus.PENDING_REVIEW)
                .build();
        mockDocument.setId(100L);
        mockDocument.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("UT-BE-09: Lấy danh sách tài liệu theo internCode hợp lệ -> Trả về danh sách DocumentResponse")
    void getDocumentsByInternCode_whenValid_shouldReturnList() {
        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockInternProfile));
        when(internDocumentRepository.findByInternProfileInternCodeOrderByCreatedAtDesc("INT-202609-0001"))
                .thenReturn(List.of(mockDocument));

        List<DocumentResponse> result = internDocumentService.getDocumentsByInternCode("INT-202609-0001");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getId());
        assertEquals("INT-202609-0001", result.get(0).getInternCode());
        assertEquals("NguyenVanA_CV.pdf", result.get(0).getOriginalFileName());
    }

    @Test
    @DisplayName("UT-BE-10: Tải file khi tồn tại trên đĩa -> Trả về DocumentDownloadDto chứa Resource")
    void downloadDocument_whenFileExists_shouldReturnResource() {
        Resource mockResource = new ByteArrayResource("PDF Content".getBytes());

        when(internDocumentRepository.findById(100L)).thenReturn(Optional.of(mockDocument));
        when(fileStorageService.loadFileAsResource("interns/INT-202609-0001/uuid-cv.pdf"))
                .thenReturn(mockResource);

        DocumentDownloadDto dto = internDocumentService.loadDocumentForDownload(100L);

        assertNotNull(dto);
        assertEquals("NguyenVanA_CV.pdf", dto.getOriginalFileName());
        assertEquals("application/pdf", dto.getContentType());
        assertNotNull(dto.getResource());
    }

    @Test
    @DisplayName("UT-BE-11: Tải file khi ổ đĩa không tìm thấy file vật lý -> Ném ResourceNotFoundException")
    void downloadDocument_whenFileNotFoundOnDisk_shouldThrowResourceNotFoundException() {
        when(internDocumentRepository.findById(100L)).thenReturn(Optional.of(mockDocument));
        when(fileStorageService.loadFileAsResource("interns/INT-202609-0001/uuid-cv.pdf"))
                .thenThrow(new ResourceNotFoundException("Tệp tin vật lý không tồn tại trên máy chủ"));

        assertThrows(ResourceNotFoundException.class, () ->
                internDocumentService.loadDocumentForDownload(100L));
    }

    @Test
    @DisplayName("UT-BE-12: Duyệt tài liệu (APPROVED) -> Cập nhật trạng thái và làm sạch rejectionReason")
    void reviewDocument_approve_shouldUpdateStatusAndClearReason() {
        mockDocument.setRejectionReason("Lý do từ chối cũ");

        ReviewDocumentRequest request = ReviewDocumentRequest.builder()
                .status(DocumentStatus.APPROVED)
                .build();

        when(internDocumentRepository.findById(100L)).thenReturn(Optional.of(mockDocument));
        when(internDocumentRepository.save(any(InternDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = internDocumentService.reviewDocument(100L, request);

        assertNotNull(response);
        assertEquals(DocumentStatus.APPROVED, response.getStatus());
        assertNull(response.getRejectionReason());
        verify(internDocumentRepository, times(1)).save(mockDocument);
    }

    @Test
    @DisplayName("UT-BE-13: Từ chối tài liệu (REJECTED) kèm lý do hợp lệ -> Cập nhật trạng thái và lý do")
    void reviewDocument_rejectWithReason_shouldUpdateStatusAndReason() {
        ReviewDocumentRequest request = ReviewDocumentRequest.builder()
                .status(DocumentStatus.REJECTED)
                .rejectionReason("CV bị lỗi định dạng và thiếu thông tin liên hệ")
                .build();

        when(internDocumentRepository.findById(100L)).thenReturn(Optional.of(mockDocument));
        when(internDocumentRepository.save(any(InternDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = internDocumentService.reviewDocument(100L, request);

        assertNotNull(response);
        assertEquals(DocumentStatus.REJECTED, response.getStatus());
        assertEquals("CV bị lỗi định dạng và thiếu thông tin liên hệ", response.getRejectionReason());
        verify(internDocumentRepository, times(1)).save(mockDocument);
    }

    @Test
    @DisplayName("UT-BE-14: Từ chối tài liệu (REJECTED) nhưng lý do rỗng hoặc dưới 5 ký tự -> Ném BadRequestException")
    void reviewDocument_rejectWithoutReason_shouldThrowBadRequestException() {
        ReviewDocumentRequest request = ReviewDocumentRequest.builder()
                .status(DocumentStatus.REJECTED)
                .rejectionReason(" ")
                .build();

        when(internDocumentRepository.findById(100L)).thenReturn(Optional.of(mockDocument));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                internDocumentService.reviewDocument(100L, request));

        assertTrue(ex.getMessage().contains("Lý do từ chối không được để trống"));
        verify(internDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-BE-15: Xét duyệt khi documentId không tồn tại -> Ném ResourceNotFoundException")
    void reviewDocument_whenDocumentNotFound_shouldThrowResourceNotFoundException() {
        ReviewDocumentRequest request = ReviewDocumentRequest.builder()
                .status(DocumentStatus.APPROVED)
                .build();

        when(internDocumentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                internDocumentService.reviewDocument(999L, request));

        verify(internDocumentRepository, never()).save(any());
    }
}
