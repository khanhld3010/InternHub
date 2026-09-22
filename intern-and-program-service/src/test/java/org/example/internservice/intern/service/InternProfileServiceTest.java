package org.example.internservice.intern.service;

import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.dto.request.CreateInternRequest;
import org.example.internservice.intern.dto.request.InternFilterRequest;
import org.example.internservice.intern.dto.request.UpdateInternRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


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

    @Test
    @DisplayName("Cap nhat ho so thuc tap sinh thanh cong khi du lieu hop le")
    void updateIntern_withValidData_shouldUpdateAndReturnResponse() {
        UpdateInternRequest updateRequest = UpdateInternRequest.builder()
                .fullName("Nguyen Van An Updated")
                .email("an.updated@gmail.com")
                .phone("0987654321")
                .dateOfBirth(LocalDate.of(2003, 5, 20))
                .gender(Gender.MALE)
                .university("DH Bach Khoa Ha Noi")
                .major("Ky thuat Phan mem")
                .academicYear("2021-2025")
                .appliedPosition("Senior Java Intern")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .status(InternStatus.APPROVED)
                .notes("Duyet ho so")
                .build();

        when(internProfileRepository.findById(1L)).thenReturn(Optional.of(savedProfile));
        when(internProfileRepository.existsByEmailAndIdNot("an.updated@gmail.com", 1L)).thenReturn(false);
        when(internProfileRepository.existsByPhoneAndIdNot("0987654321", 1L)).thenReturn(false);
        when(internProfileRepository.save(any(InternProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InternResponse response = internProfileService.updateIntern(1L, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.getFullName()).isEqualTo("Nguyen Van An Updated");
        assertThat(response.getEmail()).isEqualTo("an.updated@gmail.com");
        assertThat(response.getPhone()).isEqualTo("0987654321");
        assertThat(response.getStatus()).isEqualTo(InternStatus.APPROVED);
        assertThat(response.getInternCode()).isEqualTo("INT-202609-0001");

        verify(internProfileRepository).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nem ResourceNotFoundException khi khong tim thay ID ho so can cap nhat")
    void updateIntern_whenIdNotFound_shouldThrowResourceNotFoundException() {
        UpdateInternRequest updateRequest = UpdateInternRequest.builder()
                .fullName("Nguyen Van An")
                .email("nguyenvanan@gmail.com")
                .phone("0912345678")
                .university("DH BKHN")
                .major("CNTT")
                .appliedPosition("Backend")
                .startDate(LocalDate.of(2026, 10, 1))
                .status(InternStatus.PENDING)
                .build();

        when(internProfileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> internProfileService.updateIntern(999L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy hồ sơ thực tập sinh với ID: 999");

        verify(internProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nem IllegalArgumentException khi ngay ket thuc truoc ngay bat dau")
    void updateIntern_whenEndDateBeforeStartDate_shouldThrowIllegalArgumentException() {
        UpdateInternRequest updateRequest = UpdateInternRequest.builder()
                .fullName("Nguyen Van An")
                .email("nguyenvanan@gmail.com")
                .phone("0912345678")
                .university("DH BKHN")
                .major("CNTT")
                .appliedPosition("Backend")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 9, 1))
                .status(InternStatus.PENDING)
                .build();

        when(internProfileRepository.findById(1L)).thenReturn(Optional.of(savedProfile));

        assertThatThrownBy(() -> internProfileService.updateIntern(1L, updateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ngày kết thúc thực tập không thể trước ngày bắt đầu");

        verify(internProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nem IllegalStateException khi chuyen doi trang thai State Machine khong hop le")
    void updateIntern_whenInvalidStatusTransition_shouldThrowIllegalStateException() {
        savedProfile.setStatus(InternStatus.COMPLETED);

        UpdateInternRequest updateRequest = UpdateInternRequest.builder()
                .fullName("Nguyen Van An")
                .email("nguyenvanan@gmail.com")
                .phone("0912345678")
                .university("DH BKHN")
                .major("CNTT")
                .appliedPosition("Backend")
                .startDate(LocalDate.of(2026, 10, 1))
                .status(InternStatus.PENDING)
                .build();

        when(internProfileRepository.findById(1L)).thenReturn(Optional.of(savedProfile));

        assertThatThrownBy(() -> internProfileService.updateIntern(1L, updateRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không thể chuyển đổi trạng thái từ COMPLETED sang PENDING");

        verify(internProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nem DuplicateResourceException khi email moi bi trung voi ho so khac")
    void updateIntern_whenEmailExistsForOther_shouldThrowDuplicateResourceException() {
        UpdateInternRequest updateRequest = UpdateInternRequest.builder()
                .fullName("Nguyen Van An")
                .email("existing.other@gmail.com")
                .phone("0912345678")
                .university("DH BKHN")
                .major("CNTT")
                .appliedPosition("Backend")
                .startDate(LocalDate.of(2026, 10, 1))
                .status(InternStatus.APPROVED)
                .build();

        when(internProfileRepository.findById(1L)).thenReturn(Optional.of(savedProfile));
        when(internProfileRepository.existsByEmailAndIdNot("existing.other@gmail.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> internProfileService.updateIntern(1L, updateRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email 'existing.other@gmail.com' đã tồn tại trong hệ thống");

        verify(internProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nem DuplicateResourceException khi so dien thoai moi bi trung voi ho so khac")
    void updateIntern_whenPhoneExistsForOther_shouldThrowDuplicateResourceException() {
        UpdateInternRequest updateRequest = UpdateInternRequest.builder()
                .fullName("Nguyen Van An")
                .email("nguyenvanan@gmail.com")
                .phone("0999999999")
                .university("DH BKHN")
                .major("CNTT")
                .appliedPosition("Backend")
                .startDate(LocalDate.of(2026, 10, 1))
                .status(InternStatus.APPROVED)
                .build();

        when(internProfileRepository.findById(1L)).thenReturn(Optional.of(savedProfile));
        when(internProfileRepository.existsByEmailAndIdNot("nguyenvanan@gmail.com", 1L)).thenReturn(false);
        when(internProfileRepository.existsByPhoneAndIdNot("0999999999", 1L)).thenReturn(true);

        assertThatThrownBy(() -> internProfileService.updateIntern(1L, updateRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Số điện thoại '0999999999' đã tồn tại trong hệ thống");

        verify(internProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cap nhat thanh cong khi email va so dien thoai giu nguyen cua chinh minh")
    void updateIntern_whenEmailAndPhoneUnchanged_shouldUpdateSuccessfully() {
        UpdateInternRequest updateRequest = UpdateInternRequest.builder()
                .fullName("Nguyen Van An Updated")
                .email("nguyenvanan@gmail.com")
                .phone("0912345678")
                .university("DH BKHN")
                .major("CNTT")
                .appliedPosition("Backend")
                .startDate(LocalDate.of(2026, 10, 1))
                .status(InternStatus.PENDING)
                .build();

        when(internProfileRepository.findById(1L)).thenReturn(Optional.of(savedProfile));
        when(internProfileRepository.existsByEmailAndIdNot("nguyenvanan@gmail.com", 1L)).thenReturn(false);
        when(internProfileRepository.existsByPhoneAndIdNot("0912345678", 1L)).thenReturn(false);
        when(internProfileRepository.save(any(InternProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InternResponse response = internProfileService.updateIntern(1L, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.getFullName()).isEqualTo("Nguyen Van An Updated");
        assertThat(response.getEmail()).isEqualTo("nguyenvanan@gmail.com");

        verify(internProfileRepository).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("searchInterns: Tim kiem thanh cong voi keyword va tra ve PageResponse")
    void searchInterns_withKeyword_shouldReturnMatchingInterns() {
        InternFilterRequest request = InternFilterRequest.builder()
                .keyword("Nguyen")
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<InternProfile> page = new PageImpl<>(List.of(savedProfile), pageable, 1);

        when(internProfileRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<InternResponse> response = internProfileService.searchInterns(request, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotalItems()).isEqualTo(1);
        assertThat(response.getCurrentPage()).isEqualTo(0);
        assertThat(response.getItems().get(0).getFullName()).isEqualTo("Nguyen Van An");
        verify(internProfileRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("searchInterns: Loc chinh xac theo truong dai hoc va chuyen nganh")
    void searchInterns_withUniversityAndMajor_shouldFilterCorrectly() {
        InternFilterRequest request = InternFilterRequest.builder()
                .university("Bach Khoa")
                .major("Cong nghe thong tin")
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<InternProfile> page = new PageImpl<>(List.of(savedProfile), pageable, 1);

        when(internProfileRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<InternResponse> response = internProfileService.searchInterns(request, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getUniversity()).isEqualTo("DH Bach Khoa Ha Noi");
    }

    @Test
    @DisplayName("searchInterns: Loc theo trang thai status")
    void searchInterns_withStatusFilter_shouldReturnCorrectStatusOnly() {
        InternFilterRequest request = InternFilterRequest.builder()
                .status(InternStatus.PENDING)
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<InternProfile> page = new PageImpl<>(List.of(savedProfile), pageable, 1);

        when(internProfileRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<InternResponse> response = internProfileService.searchInterns(request, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getItems().get(0).getStatus()).isEqualTo(InternStatus.PENDING);
    }

    @Test
    @DisplayName("searchInterns: Ket qua rong khi khong tim thay ban ghi nao")
    void searchInterns_withEmptyResult_shouldReturnEmptyPageResponse() {
        InternFilterRequest request = InternFilterRequest.builder()
                .keyword("NonExistingKeyword")
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<InternProfile> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(internProfileRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        PageResponse<InternResponse> response = internProfileService.searchInterns(request, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalItems()).isZero();
        assertThat(response.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("searchInterns: Sap xep theo truong hop le fullName ASC")
    void searchInterns_withCustomSort_shouldSortBySpecifiedField() {
        InternFilterRequest request = InternFilterRequest.builder().build();
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "fullName"));
        Page<InternProfile> page = new PageImpl<>(List.of(savedProfile), pageable, 1);

        when(internProfileRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<InternResponse> response = internProfileService.searchInterns(request, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("searchInterns: Tu dong fallback ve createdAt DESC khi truyen truong sort khong hop le")
    void searchInterns_withInvalidSortField_shouldFallbackToDefaultSort() {
        InternFilterRequest request = InternFilterRequest.builder().build();
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "invalidField"));
        Page<InternProfile> page = new PageImpl<>(List.of(savedProfile), pageable, 1);

        when(internProfileRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<InternResponse> response = internProfileService.searchInterns(request, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("searchInterns: Gioi han page size toi da 100 khi client truyen size qua lon")
    void searchInterns_whenPageSizeExceedsLimit_shouldCapAtMaxPageSize() {
        InternFilterRequest request = InternFilterRequest.builder().build();
        Pageable pageable = PageRequest.of(0, 500);
        Page<InternProfile> page = new PageImpl<>(List.of(savedProfile), pageable, 1);

        when(internProfileRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<InternResponse> response = internProfileService.searchInterns(request, pageable);

        assertThat(response).isNotNull();
    }
}

