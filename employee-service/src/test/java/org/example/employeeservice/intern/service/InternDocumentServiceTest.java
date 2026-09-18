package org.example.employeeservice.intern.service;

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
import org.example.employeeservice.intern.service.impl.InternDocumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternDocumentServiceTest {

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternDocumentRepository internDocumentRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private InternDocumentServiceImpl internDocumentService;

    private InternProfile mockInternProfile;

    @BeforeEach
    void setUp() {
        mockInternProfile = InternProfile.builder()
                .internCode("INT-202609-0001")
                .fullName("Nguyễn Văn A")
                .status(org.example.employeeservice.intern.entity.enums.InternStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("UT-BE-01: Upload CV file PDF hợp lệ -> Lưu file và metadata thành công")
    void uploadDocument_validCvPdf_shouldStoreFileAndSaveMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "my_cv.pdf",
                "application/pdf",
                "Test PDF Content".getBytes()
        );

        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockInternProfile));
        when(fileStorageService.storeFile(eq(file), eq("interns/INT-202609-0001")))
                .thenReturn("random-uuid.pdf");

        InternDocument savedDoc = InternDocument.builder()
                .internProfile(mockInternProfile)
                .documentType(DocumentType.CV)
                .originalFileName("my_cv.pdf")
                .fileName("random-uuid.pdf")
                .filePath("interns/INT-202609-0001/random-uuid.pdf")
                .fileSize((long) "Test PDF Content".getBytes().length)
                .contentType("application/pdf")
                .status(DocumentStatus.PENDING_REVIEW)
                .build();
        savedDoc.setId(10L);
        savedDoc.setCreatedAt(LocalDateTime.now());

        when(internDocumentRepository.save(any(InternDocument.class))).thenReturn(savedDoc);

        DocumentResponse response = internDocumentService.uploadDocument("INT-202609-0001", file, "CV");

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("INT-202609-0001", response.getInternCode());
        assertEquals(DocumentType.CV, response.getDocumentType());
        assertEquals("my_cv.pdf", response.getOriginalFileName());
        assertEquals(DocumentStatus.PENDING_REVIEW, response.getStatus());

        verify(fileStorageService, times(1)).storeFile(eq(file), eq("interns/INT-202609-0001"));
        verify(internDocumentRepository, times(1)).save(any(InternDocument.class));
        verify(fileStorageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("UT-BE-02: Upload Đơn xin thực tập file DOCX hợp lệ -> Thành công")
    void uploadDocument_validApplicationLetterDocx_shouldSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "don_xin_thuc_tap.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "Word doc content".getBytes()
        );

        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockInternProfile));
        when(fileStorageService.storeFile(eq(file), eq("interns/INT-202609-0001")))
                .thenReturn("random-uuid-doc.docx");

        InternDocument savedDoc = InternDocument.builder()
                .internProfile(mockInternProfile)
                .documentType(DocumentType.APPLICATION_LETTER)
                .originalFileName("don_xin_thuc_tap.docx")
                .fileName("random-uuid-doc.docx")
                .filePath("interns/INT-202609-0001/random-uuid-doc.docx")
                .fileSize((long) "Word doc content".getBytes().length)
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .status(DocumentStatus.PENDING_REVIEW)
                .build();
        savedDoc.setId(11L);
        savedDoc.setCreatedAt(LocalDateTime.now());

        when(internDocumentRepository.save(any(InternDocument.class))).thenReturn(savedDoc);

        DocumentResponse response = internDocumentService.uploadDocument("INT-202609-0001", file, "APPLICATION_LETTER");

        assertNotNull(response);
        assertEquals(DocumentType.APPLICATION_LETTER, response.getDocumentType());
        assertEquals("don_xin_thuc_tap.docx", response.getOriginalFileName());
    }

    @Test
    @DisplayName("UT-BE-03: Mã internCode không tồn tại -> Ném ResourceNotFoundException")
    void uploadDocument_whenInternCodeNotFound_shouldThrowResourceNotFoundException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cv.pdf",
                "application/pdf",
                "Content".getBytes()
        );

        when(internProfileRepository.findByInternCode("INT-999999-9999"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                internDocumentService.uploadDocument("INT-999999-9999", file, "CV"));

        verify(fileStorageService, never()).storeFile(any(), any());
        verify(internDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-BE-04: File rỗng -> Ném BadRequestException")
    void uploadDocument_whenFileIsEmpty_shouldThrowBadRequestException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cv.pdf",
                "application/pdf",
                new byte[0]
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                internDocumentService.uploadDocument("INT-202609-0001", file, "CV"));

        assertEquals("Tệp tin tải lên không được để trống", ex.getMessage());
    }

    @Test
    @DisplayName("UT-BE-05: Dung lượng vượt quá 5MB -> Ném BadRequestException")
    void uploadDocument_whenFileSizeExceedsLimit_shouldThrowBadRequestException() {
        byte[] largeBytes = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large_cv.pdf",
                "application/pdf",
                largeBytes
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                internDocumentService.uploadDocument("INT-202609-0001", file, "CV"));

        assertTrue(ex.getMessage().contains("vượt quá giới hạn cho phép"));
    }

    @Test
    @DisplayName("UT-BE-06: Đuôi file không hợp lệ (.exe) -> Ném BadRequestException")
    void uploadDocument_whenInvalidFileExtension_shouldThrowBadRequestException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "virus.exe",
                "application/octet-stream",
                "Malware content".getBytes()
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                internDocumentService.uploadDocument("INT-202609-0001", file, "CV"));

        assertTrue(ex.getMessage().contains("Định dạng tệp tin không hợp lệ"));
    }

    @Test
    @DisplayName("UT-BE-07: DB lưu thất bại -> Rollback xóa file vật lý đã ghi")
    void uploadDocument_whenDbFails_shouldDeleteStoredFileAndThrowException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cv.pdf",
                "application/pdf",
                "PDF Content".getBytes()
        );

        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockInternProfile));
        when(fileStorageService.storeFile(eq(file), eq("interns/INT-202609-0001")))
                .thenReturn("unique-file.pdf");
        when(internDocumentRepository.save(any(InternDocument.class)))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () ->
                internDocumentService.uploadDocument("INT-202609-0001", file, "CV"));

        verify(fileStorageService, times(1)).deleteFile(eq("interns/INT-202609-0001/unique-file.pdf"));
    }

    @Test
    @DisplayName("UT-BE-08: Hồ sơ thực tập sinh đã đóng (COMPLETED/REJECTED) -> Ném BadRequestException")
    void uploadDocument_whenInternProfileIsClosed_shouldThrowBadRequestException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cv.pdf",
                "application/pdf",
                "PDF Content".getBytes()
        );

        mockInternProfile.setStatus(org.example.employeeservice.intern.entity.enums.InternStatus.COMPLETED);
        when(internProfileRepository.findByInternCode("INT-202609-0001"))
                .thenReturn(Optional.of(mockInternProfile));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                internDocumentService.uploadDocument("INT-202609-0001", file, "CV"));

        assertTrue(ex.getMessage().contains("Hồ sơ thực tập sinh đã đóng"));
        verify(fileStorageService, never()).storeFile(any(), any());
    }
}
