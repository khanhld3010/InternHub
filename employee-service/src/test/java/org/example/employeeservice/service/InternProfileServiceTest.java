package org.example.employeeservice.service;

import org.example.employeeservice.dto.request.CreateInternRequest;
import org.example.employeeservice.dto.response.InternResponse;
import org.example.employeeservice.entity.InternProfile;
import org.example.employeeservice.entity.enums.Gender;
import org.example.employeeservice.entity.enums.InternStatus;
import org.example.employeeservice.exception.DuplicateResourceException;
import org.example.employeeservice.repository.InternProfileRepository;
import org.example.employeeservice.service.impl.InternProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternProfileServiceTest {

    @Mock
    private InternProfileRepository internProfileRepository;

    @InjectMocks
    private InternProfileServiceImpl internProfileService;

    private CreateInternRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = CreateInternRequest.builder()
                .fullName("Lương Anh Huy")
                .email("luonganhhuy2004@gmail.com")
                .phone("0987654321")
                .dateOfBirth(LocalDate.of(2004, 1, 1))
                .gender(Gender.MALE)
                .university("Đại học Bách Khoa")
                .major("Kỹ thuật Phần mềm")
                .academicYear("Năm 4")
                .gpa(3.5)
                .appliedPosition("Backend Java Intern")
                .startDate(LocalDate.of(2026, 10, 1))
                .address("Hà Nội")
                .notes("Hồ sơ ứng viên xuất sắc")
                .build();
    }

    @Test
    @DisplayName("Tạo hồ sơ thành công khi dữ liệu hợp lệ")
    void createIntern_withValidData_shouldSaveAndReturnResponse() {
        when(internProfileRepository.existsByEmail("luonganhhuy2004@gmail.com")).thenReturn(false);
        when(internProfileRepository.existsByPhone("0987654321")).thenReturn(false);
        when(internProfileRepository.countByInternCodeStartingWith(anyString())).thenReturn(0L);
        when(internProfileRepository.existsByInternCode(anyString())).thenReturn(false);

        InternProfile savedProfile = InternProfile.builder()
                .internCode("INT-202609-0001")
                .fullName(validRequest.getFullName())
                .email(validRequest.getEmail())
                .phone(validRequest.getPhone())
                .status(InternStatus.PENDING)
                .appliedPosition(validRequest.getAppliedPosition())
                .university(validRequest.getUniversity())
                .major(validRequest.getMajor())
                .build();
        savedProfile.setId(1L);

        when(internProfileRepository.save(any(InternProfile.class))).thenReturn(savedProfile);

        InternResponse response = internProfileService.createIntern(validRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("INT-202609-0001", response.getInternCode());
        assertEquals("Lương Anh Huy", response.getFullName());
        assertEquals(InternStatus.PENDING, response.getStatus());

        verify(internProfileRepository).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Ném lỗi DuplicateResourceException khi email đã tồn tại")
    void createIntern_whenEmailExists_shouldThrowDuplicateResourceException() {
        when(internProfileRepository.existsByEmail("luonganhhuy2004@gmail.com")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> internProfileService.createIntern(validRequest)
        );

        assertEquals("Email 'luonganhhuy2004@gmail.com' đã tồn tại trong hệ thống", exception.getMessage());
        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Ném lỗi DuplicateResourceException khi số điện thoại đã tồn tại")
    void createIntern_whenPhoneExists_shouldThrowDuplicateResourceException() {
        when(internProfileRepository.existsByEmail("luonganhhuy2004@gmail.com")).thenReturn(false);
        when(internProfileRepository.existsByPhone("0987654321")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> internProfileService.createIntern(validRequest)
        );

        assertEquals("Số điện thoại '0987654321' đã tồn tại trong hệ thống", exception.getMessage());
        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }
}
