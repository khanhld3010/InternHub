package org.example.internservice.intern.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.intern.dto.request.ApplyInternRequest;
import org.example.internservice.intern.dto.response.InternResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.Gender;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.service.impl.InternProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternProfileServiceApplyTest {

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InternProfileServiceImpl internProfileService;

    private ApplyInternRequest validApplyRequest;

    @BeforeEach
    void setUp() {
        validApplyRequest = ApplyInternRequest.builder()
                .userId(15L)
                .fullName("Nguyễn Văn A")
                .email("nguyenvana@gmail.com")
                .phone("0987654321")
                .dateOfBirth(LocalDate.of(2003, 5, 15))
                .gender(Gender.MALE)
                .address("Hà Nội")
                .university("Đại học Bách Khoa")
                .major("Công nghệ thông tin")
                .academicYear("2021-2025")
                .appliedPosition("Java Backend Developer")
                .startDate(LocalDate.now().plusDays(7))
                .notes("Mong muốn thực tập full-time")
                .build();
    }

    @Test
    @DisplayName("Nộp hồ sơ trực tuyến thành công: lưu userId, tự sinh mã internCode và status PENDING")
    void givenValidApplyRequest_whenApplyOnline_thenSuccessWithUserIdAndInternCode() {
        // Arrange
        when(internProfileRepository.existsByUserIdAndStatusIn(eq(15L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmailAndStatusIn(eq("nguyenvana@gmail.com"), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
        when(internProfileRepository.existsByPhone("0987654321")).thenReturn(false);
        when(internProfileRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);

        InternProfile savedProfile = InternProfile.builder()
                .userId(15L)
                .internCode("INT-2026-0001")
                .fullName(validApplyRequest.getFullName())
                .email(validApplyRequest.getEmail())
                .phone(validApplyRequest.getPhone())
                .university(validApplyRequest.getUniversity())
                .major(validApplyRequest.getMajor())
                .appliedPosition(validApplyRequest.getAppliedPosition())
                .startDate(validApplyRequest.getStartDate())
                .status(InternStatus.PENDING)
                .build();
        savedProfile.setId(100L);

        when(internProfileRepository.save(any(InternProfile.class))).thenReturn(savedProfile);

        // Act
        InternResponse response = internProfileService.applyOnline(validApplyRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getUserId()).isEqualTo(15L);
        assertThat(response.getInternCode()).isEqualTo("INT-2026-0001");
        assertThat(response.getStatus()).isEqualTo(InternStatus.PENDING);
        assertThat(response.getFullName()).isEqualTo("Nguyễn Văn A");

        verify(internProfileRepository, times(1)).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: tài khoản đã có hồ sơ đang xử lý (PENDING/APPROVED/INTERNING)")
    void givenActiveApplicationForUserId_whenApplyOnline_thenThrowBadRequestException() {
        when(internProfileRepository.existsByUserIdAndStatusIn(eq(15L), anyList())).thenReturn(true);

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn đã có một hồ sơ đang chờ xét duyệt hoặc đang trong quá trình thực tập");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: email đã có hồ sơ đang xử lý")
    void givenEmailInProcess_whenApplyOnline_thenThrowBadRequestException() {
        when(internProfileRepository.existsByUserIdAndStatusIn(eq(15L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmailAndStatusIn(eq("nguyenvana@gmail.com"), anyList())).thenReturn(true);

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đang chờ xét duyệt hoặc đang trong quá trình thực tập");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: email đã tồn tại trong hệ thống")
    void givenDuplicateEmail_whenApplyOnline_thenThrowDuplicateResourceException() {
        when(internProfileRepository.existsByUserIdAndStatusIn(eq(15L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmailAndStatusIn(eq("nguyenvana@gmail.com"), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email 'nguyenvana@gmail.com' đã tồn tại trong hệ thống");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: ngày kết thúc trước ngày bắt đầu")
    void givenInvalidEndDateBeforeStartDate_whenApplyOnline_thenThrowBadRequestException() {
        validApplyRequest.setStartDate(LocalDate.of(2026, 10, 10));
        validApplyRequest.setEndDate(LocalDate.of(2026, 10, 1)); // Lỗi: trước startDate

        when(internProfileRepository.existsByUserIdAndStatusIn(eq(15L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmailAndStatusIn(eq("nguyenvana@gmail.com"), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
        when(internProfileRepository.existsByPhone("0987654321")).thenReturn(false);

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ngày kết thúc thực tập không thể trước ngày bắt đầu");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }
}
