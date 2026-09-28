package org.example.internservice.intern.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.dto.request.ApplyInternRequest;
import org.example.internservice.intern.dto.response.InternResponse;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.Gender;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.service.impl.InternProfileServiceImpl;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.InternshipProgramRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternProfileServiceApplyTest {

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternshipProgramRepository programRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InternProfileServiceImpl internProfileService;

    private ApplyInternRequest validApplyRequest;
    private InternshipProgram openProgram;

    @BeforeEach
    void setUp() {
        Department department = Department.builder()
                .name("Trung tâm Phần mềm")
                .build();
        department.setId(1L);

        openProgram = InternshipProgram.builder()
                .programCode("PRG-202609-0001")
                .name("Chương trình Thực tập sinh Java Backend 2026")
                .department(department)
                .status(ProgramStatus.OPEN)
                .isRecruitmentOpen(true)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .maxInterns(20)
                .currentInterns(5)
                .build();
        openProgram.setId(10L);

        validApplyRequest = ApplyInternRequest.builder()
                .userId(15L)
                .programId(10L)
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
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .notes("Mong muốn thực tập full-time")
                .build();
    }

    @Test
    @DisplayName("AC-7: Nộp hồ sơ trực tuyến thành công: liên kết programId, tự sinh mã internCode và status PENDING")
    void givenValidApplyRequest_whenApplyOnline_thenSuccessWithProgramLinkage() {
        // Arrange
        when(programRepository.findById(10L)).thenReturn(Optional.of(openProgram));
        when(internProfileRepository.existsByUserIdAndProgramIdAndStatusIn(eq(15L), eq(10L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmailAndProgramIdAndStatusIn(eq("nguyenvana@gmail.com"), eq(10L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByPhoneAndProgramIdAndStatusIn(eq("0987654321"), eq(10L), anyList())).thenReturn(false);
        when(internProfileRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);

        InternProfile savedProfile = InternProfile.builder()
                .userId(15L)
                .program(openProgram)
                .internCode("INT-2026-0001")
                .fullName(validApplyRequest.getFullName())
                .email(validApplyRequest.getEmail())
                .phone(validApplyRequest.getPhone())
                .university(validApplyRequest.getUniversity())
                .major(validApplyRequest.getMajor())
                .appliedPosition(validApplyRequest.getAppliedPosition())
                .startDate(validApplyRequest.getStartDate())
                .endDate(validApplyRequest.getEndDate())
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
        assertThat(response.getProgramId()).isEqualTo(10L);
        assertThat(response.getProgramCode()).isEqualTo("PRG-202609-0001");
        assertThat(response.getProgramName()).isEqualTo("Chương trình Thực tập sinh Java Backend 2026");
        assertThat(response.getInternCode()).isEqualTo("INT-2026-0001");
        assertThat(response.getStatus()).isEqualTo(InternStatus.PENDING);
        assertThat(response.getFullName()).isEqualTo("Nguyễn Văn A");

        verify(internProfileRepository, times(1)).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("AC-8: Kế thừa ngày bắt đầu/kết thúc từ chương trình nếu ứng viên để trống")
    void givenEmptyDatesInRequest_whenApplyOnline_thenInheritDatesFromProgram() {
        // Arrange
        validApplyRequest.setStartDate(null);
        validApplyRequest.setEndDate(null);

        when(programRepository.findById(10L)).thenReturn(Optional.of(openProgram));
        when(internProfileRepository.existsByUserIdAndProgramIdAndStatusIn(eq(15L), eq(10L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmailAndProgramIdAndStatusIn(eq("nguyenvana@gmail.com"), eq(10L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByPhoneAndProgramIdAndStatusIn(eq("0987654321"), eq(10L), anyList())).thenReturn(false);
        when(internProfileRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);

        when(internProfileRepository.save(any(InternProfile.class))).thenAnswer(invocation -> {
            InternProfile p = invocation.getArgument(0);
            p.setId(101L);
            return p;
        });

        // Act
        InternResponse response = internProfileService.applyOnline(validApplyRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStartDate()).isEqualTo(openProgram.getStartDate());
        assertThat(response.getEndDate()).isEqualTo(openProgram.getEndDate());
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: không tìm thấy chương trình thực tập (ResourceNotFoundException)")
    void givenNonExistentProgram_whenApplyOnline_thenThrowResourceNotFoundException() {
        when(programRepository.findById(999L)).thenReturn(Optional.empty());
        validApplyRequest.setProgramId(999L);

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy chương trình thực tập với ID: 999");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: chương trình tạm đóng tuyển sinh (isRecruitmentOpen = false)")
    void givenClosedRecruitmentProgram_whenApplyOnline_thenThrowBadRequestException() {
        openProgram.setIsRecruitmentOpen(false);
        when(programRepository.findById(10L)).thenReturn(Optional.of(openProgram));

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chương trình thực tập hiện đang tạm dừng nhận hồ sơ tuyển sinh");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: chương trình ở trạng thái không nhận hồ sơ (CANCELLED)")
    void givenCancelledProgram_whenApplyOnline_thenThrowBadRequestException() {
        openProgram.setStatus(ProgramStatus.CANCELLED);
        when(programRepository.findById(10L)).thenReturn(Optional.of(openProgram));

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chương trình thực tập không ở trạng thái mở nhận hồ sơ");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: cùng tài khoản đã có hồ sơ đang xử lý trong CÙNG chương trình")
    void givenActiveApplicationInSameProgramForUserId_whenApplyOnline_thenThrowBadRequestException() {
        when(programRepository.findById(10L)).thenReturn(Optional.of(openProgram));
        when(internProfileRepository.existsByUserIdAndProgramIdAndStatusIn(eq(15L), eq(10L), anyList())).thenReturn(true);

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn đã có một hồ sơ đang chờ xét duyệt hoặc đang thực tập trong chương trình này");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: email đã có hồ sơ đang xử lý trong CÙNG chương trình")
    void givenEmailInSameProgram_whenApplyOnline_thenThrowBadRequestException() {
        when(programRepository.findById(10L)).thenReturn(Optional.of(openProgram));
        when(internProfileRepository.existsByUserIdAndProgramIdAndStatusIn(eq(15L), eq(10L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmailAndProgramIdAndStatusIn(eq("nguyenvana@gmail.com"), eq(10L), anyList())).thenReturn(true);

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đang chờ xét duyệt hoặc đang thực tập trong chương trình này");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("AC-9: Cho phép cùng tài khoản nộp hồ sơ vào chương trình B dù đã có hồ sơ ở chương trình A")
    void givenAppliedInProgramA_whenApplyInProgramB_thenSuccess() {
        // Arrange
        InternshipProgram programB = InternshipProgram.builder()
                .programCode("PRG-202609-0002")
                .name("Chương trình Thực tập sinh Frontend React 2026")
                .status(ProgramStatus.OPEN)
                .isRecruitmentOpen(true)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2027, 2, 1))
                .build();
        programB.setId(20L);

        validApplyRequest.setProgramId(20L);
        when(programRepository.findById(20L)).thenReturn(Optional.of(programB));
        when(internProfileRepository.existsByUserIdAndProgramIdAndStatusIn(eq(15L), eq(20L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmailAndProgramIdAndStatusIn(eq("nguyenvana@gmail.com"), eq(20L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByPhoneAndProgramIdAndStatusIn(eq("0987654321"), eq(20L), anyList())).thenReturn(false);
        when(internProfileRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);

        when(internProfileRepository.save(any(InternProfile.class))).thenAnswer(invocation -> {
            InternProfile p = invocation.getArgument(0);
            p.setId(102L);
            return p;
        });

        // Act
        InternResponse response = internProfileService.applyOnline(validApplyRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getProgramId()).isEqualTo(20L);
        assertThat(response.getStatus()).isEqualTo(InternStatus.PENDING);
        verify(internProfileRepository, times(1)).save(any(InternProfile.class));
    }

    @Test
    @DisplayName("Nộp hồ sơ thất bại: ngày kết thúc trước ngày bắt đầu")
    void givenInvalidEndDateBeforeStartDate_whenApplyOnline_thenThrowBadRequestException() {
        validApplyRequest.setStartDate(LocalDate.of(2026, 10, 10));
        validApplyRequest.setEndDate(LocalDate.of(2026, 10, 1)); // Lỗi: trước startDate

        when(programRepository.findById(10L)).thenReturn(Optional.of(openProgram));
        when(internProfileRepository.existsByUserIdAndProgramIdAndStatusIn(eq(15L), eq(10L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByEmailAndProgramIdAndStatusIn(eq("nguyenvana@gmail.com"), eq(10L), anyList())).thenReturn(false);
        when(internProfileRepository.existsByPhoneAndProgramIdAndStatusIn(eq("0987654321"), eq(10L), anyList())).thenReturn(false);

        assertThatThrownBy(() -> internProfileService.applyOnline(validApplyRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ngày kết thúc thực tập không thể trước ngày bắt đầu");

        verify(internProfileRepository, never()).save(any(InternProfile.class));
    }
}
