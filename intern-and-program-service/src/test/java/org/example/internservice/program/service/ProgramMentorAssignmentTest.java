package org.example.internservice.program.service;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.client.IdentityServiceClient;
import org.example.internservice.intern.entity.InternMentorAssignment;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.entity.enums.MentorAssignmentStatus;
import org.example.internservice.intern.event.InternMentorAssignedEvent;
import org.example.internservice.intern.repository.InternMentorAssignmentRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.program.dto.request.AssignMentorToProgramRequest;
import org.example.internservice.program.dto.response.AssignMentorToProgramResponse;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.ProgramMentor;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.DepartmentRepository;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.repository.ProgramCodeSequenceRepository;
import org.example.internservice.program.repository.ProgramMentorRepository;
import org.example.internservice.program.service.impl.InternshipProgramServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramMentorAssignmentTest {

    @Mock
    private InternshipProgramRepository programRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ProgramCodeSequenceRepository sequenceRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private ProgramMentorRepository programMentorRepository;

    @Mock
    private InternMentorAssignmentRepository internMentorAssignmentRepository;

    @Mock
    private IdentityServiceClient identityServiceClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InternshipProgramServiceImpl programService;

    private InternshipProgram testProgram;
    private MentorProfile activeMentor;
    private Department department;

    @BeforeEach
    void setUp() {
        department = Department.builder()
                .name("Kỹ thuật Phần mềm")
                .code("SE")
                .status("ACTIVE")
                .build();
        department.setId(1L);

        testProgram = InternshipProgram.builder()
                .programCode("PRG-202610-0001")
                .name("Chương trình Thực tập Java Web Fullstack")
                .department(department)
                .status(ProgramStatus.OPEN)
                .isRecruitmentOpen(true)
                .maxInterns(20)
                .currentInterns(2)
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusMonths(3))
                .build();
        testProgram.setId(100L);

        activeMentor = MentorProfile.builder()
                .fullName("Nguyễn Văn Hướng Dẫn")
                .email("mentor.huongdan@internhub.vn")
                .phone("0987654321")
                .department(department)
                .status("ACTIVE")
                .build();
        activeMentor.setId(10L);
    }

    @Test
    @DisplayName("TM-101: Gán Mentor cho cả Program thành công khi TTS chưa có Mentor nào")
    void assignMentorToProgram_success_newAssignment() {
        // Given
        AssignMentorToProgramRequest request = AssignMentorToProgramRequest.builder()
                .mentorId(10L)
                .notes("Phân công cho kỳ Thực tập Spring Boot")
                .build();

        InternProfile intern1 = InternProfile.builder()
                .internCode("INT-202610-0001")
                .fullName("Trần Văn An")
                .email("an.tv@example.com")
                .status(InternStatus.APPROVED)
                .program(testProgram)
                .build();
        intern1.setId(1L);

        InternProfile intern2 = InternProfile.builder()
                .internCode("INT-202610-0002")
                .fullName("Lê Thị Bình")
                .email("binh.lt@example.com")
                .status(InternStatus.APPROVED)
                .program(testProgram)
                .build();
        intern2.setId(2L);

        when(programRepository.findById(100L)).thenReturn(Optional.of(testProgram));
        when(mentorProfileRepository.findById(10L)).thenReturn(Optional.of(activeMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(100L, 10L)).thenReturn(false);
        when(internProfileRepository.findByProgramIdAndStatusIn(eq(100L), anyCollection()))
                .thenReturn(List.of(intern1, intern2));
        when(internMentorAssignmentRepository.findByInternIdInAndStatus(anyCollection(), eq(MentorAssignmentStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        // When
        AssignMentorToProgramResponse response = programService.assignMentorToProgram(100L, request, "hr_manager");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getProgramId()).isEqualTo(100L);
        assertThat(response.getMentorId()).isEqualTo(10L);
        assertThat(response.getMentorName()).isEqualTo("Nguyễn Văn Hướng Dẫn");
        assertThat(response.getTotalAssignedInterns()).isEqualTo(2);
        assertThat(response.getReplacedMentorsCount()).isEqualTo(0);
        assertThat(response.getAffectedInternCodes()).containsExactly("INT-202610-0001", "INT-202610-0002");

        // Verify entity state updates
        assertThat(intern1.getMentorId()).isEqualTo(10L);
        assertThat(intern1.getMentorName()).isEqualTo("Nguyễn Văn Hướng Dẫn");
        assertThat(intern2.getMentorId()).isEqualTo(10L);

        // Verify ProgramMentor created
        verify(programMentorRepository).save(any(ProgramMentor.class));
        verify(internProfileRepository).saveAll(anyCollection());
        verify(eventPublisher, times(2)).publishEvent(any(InternMentorAssignedEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(org.example.internservice.system.audit.event.AuditLogEvent.class));
    }

    @Test
    @DisplayName("TM-101: Gán Mentor cho cả Program thành công và Thay thế Mentor cũ (Replaced)")
    void assignMentorToProgram_success_replacingExistingMentor() {
        // Given
        AssignMentorToProgramRequest request = AssignMentorToProgramRequest.builder()
                .mentorId(10L)
                .notes("Đổi mentor kỳ thực tập")
                .build();

        InternProfile internWithOldMentor = InternProfile.builder()
                .internCode("INT-202610-0001")
                .fullName("Trần Văn An")
                .email("an.tv@example.com")
                .status(InternStatus.APPROVED)
                .program(testProgram)
                .mentorId(99L)
                .mentorName("Mentor Cũ")
                .mentorEmail("old.mentor@internhub.vn")
                .build();
        internWithOldMentor.setId(1L);

        InternMentorAssignment oldAssignment = InternMentorAssignment.builder()
                .intern(internWithOldMentor)
                .mentorId(99L)
                .mentorName("Mentor Cũ")
                .mentorEmail("old.mentor@internhub.vn")
                .status(MentorAssignmentStatus.ACTIVE)
                .assignedAt(LocalDateTime.now().minusWeeks(1))
                .assignedBy("admin")
                .build();
        oldAssignment.setId(500L);

        when(programRepository.findById(100L)).thenReturn(Optional.of(testProgram));
        when(mentorProfileRepository.findById(10L)).thenReturn(Optional.of(activeMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(100L, 10L)).thenReturn(true);
        when(internProfileRepository.findByProgramIdAndStatusIn(eq(100L), anyCollection()))
                .thenReturn(List.of(internWithOldMentor));
        when(internMentorAssignmentRepository.findByInternIdInAndStatus(anyCollection(), eq(MentorAssignmentStatus.ACTIVE)))
                .thenReturn(List.of(oldAssignment));

        // When
        AssignMentorToProgramResponse response = programService.assignMentorToProgram(100L, request, "hr_manager");

        // Then
        assertThat(response.getTotalAssignedInterns()).isEqualTo(1);
        assertThat(response.getReplacedMentorsCount()).isEqualTo(1);
        assertThat(internWithOldMentor.getMentorId()).isEqualTo(10L);
        assertThat(oldAssignment.getStatus()).isEqualTo(MentorAssignmentStatus.REPLACED);
        assertThat(oldAssignment.getRevocationReason()).contains("Thay thế người hướng dẫn theo kỳ thực tập");

        verify(internMentorAssignmentRepository, times(2)).saveAll(anyCollection()); // 1 for old updated, 1 for new created
    }

    @Test
    @DisplayName("TM-101: Cơ chế điều kiện kép - Program ONGOING + Intern APPROVED -> Tự động chuyển INTERNING")
    void assignMentorToProgram_dualCondition_statusTransitsToInterning() {
        // Given
        testProgram.setStatus(ProgramStatus.ONGOING);

        AssignMentorToProgramRequest request = AssignMentorToProgramRequest.builder()
                .mentorId(10L)
                .build();

        InternProfile internApproved = InternProfile.builder()
                .internCode("INT-202610-0001")
                .fullName("Trần Văn An")
                .status(InternStatus.APPROVED)
                .program(testProgram)
                .build();
        internApproved.setId(1L);

        when(programRepository.findById(100L)).thenReturn(Optional.of(testProgram));
        when(mentorProfileRepository.findById(10L)).thenReturn(Optional.of(activeMentor));
        when(internProfileRepository.findByProgramIdAndStatusIn(eq(100L), anyCollection()))
                .thenReturn(List.of(internApproved));
        when(internMentorAssignmentRepository.findByInternIdInAndStatus(anyCollection(), eq(MentorAssignmentStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        // When
        programService.assignMentorToProgram(100L, request, "hr_lead");

        // Then: Intern status automatically transits to INTERNING
        assertThat(internApproved.getStatus()).isEqualTo(InternStatus.INTERNING);
    }

    @Test
    @DisplayName("TM-101: Ném BadRequestException nếu Mentor chưa kích hoạt (status != ACTIVE)")
    void assignMentorToProgram_inactiveMentor_throwsBadRequestException() {
        // Given
        MentorProfile inactiveMentor = MentorProfile.builder()
                .fullName("Chưa Kích Hoạt")
                .email("inactive@internhub.vn")
                .status("PENDING_ACTIVATION")
                .build();
        inactiveMentor.setId(20L);

        AssignMentorToProgramRequest request = AssignMentorToProgramRequest.builder()
                .mentorId(20L)
                .build();

        when(programRepository.findById(100L)).thenReturn(Optional.of(testProgram));
        when(mentorProfileRepository.findById(20L)).thenReturn(Optional.of(inactiveMentor));

        // When & Then
        assertThatThrownBy(() -> programService.assignMentorToProgram(100L, request, "hr_manager"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chưa kích hoạt tài khoản");
    }

    @Test
    @DisplayName("TM-101: Ném ResourceNotFoundException nếu không tìm thấy chương trình")
    void assignMentorToProgram_programNotFound_throwsResourceNotFoundException() {
        AssignMentorToProgramRequest request = AssignMentorToProgramRequest.builder()
                .mentorId(10L)
                .build();

        when(programRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programService.assignMentorToProgram(999L, request, "hr_manager"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy chương trình thực tập");
    }

    @Test
    @DisplayName("TM-101: Phân công Mentor khi Program chưa có TTS nào (totalAssignedInterns = 0)")
    void assignMentorToProgram_emptyInterns_associatesProgramMentor() {
        AssignMentorToProgramRequest request = AssignMentorToProgramRequest.builder()
                .mentorId(10L)
                .build();

        when(programRepository.findById(100L)).thenReturn(Optional.of(testProgram));
        when(mentorProfileRepository.findById(10L)).thenReturn(Optional.of(activeMentor));
        when(internProfileRepository.findByProgramIdAndStatusIn(eq(100L), anyCollection()))
                .thenReturn(Collections.emptyList());

        AssignMentorToProgramResponse response = programService.assignMentorToProgram(100L, request, "hr_manager");

        assertThat(response.getTotalAssignedInterns()).isEqualTo(0);
        assertThat(response.getReplacedMentorsCount()).isEqualTo(0);
        assertThat(response.getAffectedInternCodes()).isEmpty();
        verify(programMentorRepository).save(any(ProgramMentor.class));
        verify(internProfileRepository, never()).saveAll(anyCollection());
    }
}
