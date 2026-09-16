package org.example.employeeservice.intern.service;

import org.example.employeeservice.exception.DuplicateResourceException;
import org.example.employeeservice.intern.dto.request.CreateInternRequest;
import org.example.employeeservice.intern.dto.response.InternResponse;
import org.example.employeeservice.intern.entity.InternProfile;
import org.example.employeeservice.intern.entity.enums.Gender;
import org.example.employeeservice.intern.entity.enums.InternStatus;
import org.example.employeeservice.intern.repository.InternProfileRepository;
import org.example.employeeservice.intern.service.impl.InternProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private InternProfile savedProfile;

    @BeforeEach
    void setUp() {
        validRequest = CreateInternRequest.builder()
                .fullName("Nguyen Van An")
                .email("nguyenvanan@gmail.com")
                .phone("0912345678")
                .dateOfBirth(LocalDate.of(2003, 5, 20))
                .gender(Gender.MALE)
                .university("DH Bach Khoa Ha Noi")
                .major("Cong nghe thong tin")
                .academicYear("2021-2025")
                .appliedPosition("Backend Developer")
                .startDate(LocalDate.of(2026, 10, 1))
                .build();

        savedProfile = InternProfile.builder()
                .internCode("INT-202609-0001")
                .fullName("Nguyen Van An")
                .email("nguyenvanan@gmail.com")
                .phone("0912345678")
                .dateOfBirth(LocalDate.of(2003, 5, 20))
                .gender(Gender.MALE)
                .university("DH Bach Khoa Ha Noi")
                .major("Cong nghe thong tin")
                .academicYear("2021-2025")
                .appliedPosition("Backend Developer")
                .startDate(LocalDate.of(2026, 10, 1))
                .status(InternStatus.PENDING)
                .build();
        savedProfile.setId(1L);
        savedProfile.setCreatedAt(LocalDateTime.now());
        savedProfile.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Tao moi thuc tap sinh thanh cong khi du lieu hop le")
    void createIntern_withValidData_shouldSaveAndReturnResponse() {
        when(internProfileRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
        when(internProfileRepository.existsByPhone(validRequest.getPhone())).thenReturn(false);
        when(internProfileRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(internProfileRepository.save(any(InternProfile.class))).thenReturn(savedProfile);

        InternResponse response = internProfileService.createIntern(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getInternCode()).isEqualTo("INT-202609-0001");
        assertThat(response.getEmail()).isEqualTo("nguyenvanan@gmail.com");
        assertThat(response.getStatus()).isEqualTo(InternStatus.PENDING);

        verify(internProfileRepository).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nem ngoai le DuplicateResourceException khi email da ton tai")
    void createIntern_whenEmailExists_shouldThrowDuplicateResourceException() {
        when(internProfileRepository.existsByEmail(validRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> internProfileService.createIntern(validRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email 'nguyenvanan@gmail.com' đã tồn tại trong hệ thống");

        verify(internProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nem ngoai le DuplicateResourceException khi so dien thoai da ton tai")
    void createIntern_whenPhoneExists_shouldThrowDuplicateResourceException() {
        when(internProfileRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
        when(internProfileRepository.existsByPhone(validRequest.getPhone())).thenReturn(true);

        assertThatThrownBy(() -> internProfileService.createIntern(validRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Số điện thoại '0912345678' đã tồn tại trong hệ thống");

        verify(internProfileRepository, never()).save(any());
    }
}
