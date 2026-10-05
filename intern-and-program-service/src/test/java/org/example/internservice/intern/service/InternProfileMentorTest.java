package org.example.internservice.intern.service;

import org.example.internservice.intern.client.IdentityServiceClient;
import org.example.internservice.intern.dto.request.AssignMentorRequest;
import org.example.internservice.intern.dto.request.RevokeMentorRequest;
import org.example.internservice.intern.dto.response.InternResponse;
import org.example.internservice.intern.entity.InternMentorAssignment;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.entity.enums.MentorAssignmentStatus;
import org.example.internservice.intern.repository.InternMentorAssignmentRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.service.impl.InternProfileServiceImpl;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.example.internservice.intern.repository.MentorProfileRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternProfileMentorTest {

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternMentorAssignmentRepository internMentorAssignmentRepository;

    @Mock
    private IdentityServiceClient identityServiceClient;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private org.example.internservice.program.repository.ProgramMentorRepository programMentorRepository;

    @Mock
    private org.example.internservice.program.repository.DepartmentRepository departmentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InternProfileServiceImpl internProfileService;

    private InternProfile approvedIntern;
    private InternshipProgram ongoingProgram;
    private InternshipProgram draftProgram;
    private Map<String, Object> mentorUserMap;

    @BeforeEach
    void setUp() {
        ongoingProgram = InternshipProgram.builder()
                .programCode("PROG-2026-01")
                .name("Chương trình Thực tập Java Spring")
                .status(ProgramStatus.ONGOING)
                .build();
        ongoingProgram.setId(100L);

        draftProgram = InternshipProgram.builder()
                .programCode("PROG-2026-02")
                .name("Chương trình Thực tập Golang")
                .status(ProgramStatus.PLANNING)
                .build();
        draftProgram.setId(101L);

        approvedIntern = InternProfile.builder()
                .internCode("INT-202609-0001")
                .fullName("Trần Minh Tuấn")
                .email("tuan.tm@internhub.vn")
                .status(InternStatus.APPROVED)
                .program(ongoingProgram)
                .needsReassignment(false)
                .needsMentorReassignment(false)
                .build();
        approvedIntern.setId(1L);

        mentorUserMap = new HashMap<>();
        mentorUserMap.put("id", 20L);
        mentorUserMap.put("fullName", "Nguyễn Mentor");
        mentorUserMap.put("email", "mentor.nguyen@company.com");
        mentorUserMap.put("phoneNumber", "0987654321");
        mentorUserMap.put("status", "ACTIVE");
        mentorUserMap.put("role", "MENTOR");
    }

    @Nested
    @DisplayName("Tests cho chức năng Assign Mentor")
    class AssignMentorTests {

        @Test
        @DisplayName("Gán mentor thành công và tự động chuyển INTERNING khi program đang ONGOING (quy tắc điều kiện kép)")
        void assignMentor_Success_TransitionsToInterning_WhenProgramOngoing() {
            AssignMentorRequest request = AssignMentorRequest.builder()
                    .mentorId(20L)
                    .notes("Phân công đợt 1")
                    .build();

            when(internProfileRepository.findById(1L)).thenReturn(Optional.of(approvedIntern));
            when(identityServiceClient.getAllUsers()).thenReturn(List.of(mentorUserMap));
            when(internProfileRepository.save(any(InternProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

            InternResponse response = internProfileService.assignMentor(1L, request, "HR-Admin");

            assertThat(response).isNotNull();
            assertThat(response.getMentorId()).isEqualTo(20L);
            assertThat(response.getMentorName()).isEqualTo("Nguyễn Mentor");
            // Thỏa mãn điều kiện kép: mentorId != null && program.status == ONGOING -> Chuyển INTERNING!
            assertThat(response.getStatus()).isEqualTo(InternStatus.INTERNING);
            assertThat(response.getNeedsMentorReassignment()).isFalse();

            verify(internMentorAssignmentRepository).save(any(InternMentorAssignment.class));
            verify(internProfileRepository).save(any(InternProfile.class));
        }

        @Test
        @DisplayName("Gán mentor thành công nhưng giữ APPROVED khi program chưa ONGOING (DRAFT)")
        void assignMentor_Success_RemainsApproved_WhenProgramNotOngoing() {
            approvedIntern.setProgram(draftProgram);
            AssignMentorRequest request = AssignMentorRequest.builder()
                    .mentorId(20L)
                    .notes("Phân công trước")
                    .build();

            when(internProfileRepository.findById(1L)).thenReturn(Optional.of(approvedIntern));
            when(identityServiceClient.getAllUsers()).thenReturn(List.of(mentorUserMap));
            when(internProfileRepository.save(any(InternProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

            InternResponse response = internProfileService.assignMentor(1L, request, "HR-Admin");

            assertThat(response).isNotNull();
            assertThat(response.getMentorId()).isEqualTo(20L);
            // Program đang DRAFT -> Chưa chuyển INTERNING
            assertThat(response.getStatus()).isEqualTo(InternStatus.APPROVED);
        }

        @Test
        @DisplayName("Chặn phân công mentor khi thực tập sinh đang chờ điều phối chương trình (needsReassignment = true)")
        void assignMentor_ThrowsException_WhenNeedsProgramReassignment() {
            approvedIntern.setNeedsReassignment(true);
            AssignMentorRequest request = AssignMentorRequest.builder().mentorId(20L).build();

            when(internProfileRepository.findById(1L)).thenReturn(Optional.of(approvedIntern));

            assertThatThrownBy(() -> internProfileService.assignMentor(1L, request, "HR-Admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chờ điều phối chương trình");

            verify(internProfileRepository, never()).save(any());
        }

        @Test
        @DisplayName("Thay thế mentor: yêu cầu replaceReason bắt buộc khi TTS đã có mentor active")
        void assignMentor_ThrowsException_WhenReplacingWithoutReason() {
            approvedIntern.setMentorId(99L);
            approvedIntern.setMentorName("Mentor Cu");

            AssignMentorRequest request = AssignMentorRequest.builder()
                    .mentorId(20L)
                    .replaceReason("") // Thiếu lý do thay thế
                    .build();

            when(internProfileRepository.findById(1L)).thenReturn(Optional.of(approvedIntern));
            when(identityServiceClient.getAllUsers()).thenReturn(List.of(mentorUserMap));

            assertThatThrownBy(() -> internProfileService.assignMentor(1L, request, "HR-Admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Vui lòng nhập lý do thay đổi người hướng dẫn");
        }

        @Test
        @DisplayName("Thay thế mentor thành công: chuyển bản ghi cũ sang REPLACED và tạo bản ghi mới ACTIVE")
        void assignMentor_Success_ReplacesOldMentor() {
            approvedIntern.setMentorId(99L);
            approvedIntern.setMentorName("Mentor Cu");

            InternMentorAssignment oldAssignment = InternMentorAssignment.builder()
                    .intern(approvedIntern)
                    .mentorId(99L)
                    .status(MentorAssignmentStatus.ACTIVE)
                    .build();

            AssignMentorRequest request = AssignMentorRequest.builder()
                    .mentorId(20L)
                    .replaceReason("Mentor cũ đổi bộ phận")
                    .notes("Bàn giao công việc")
                    .build();

            when(internProfileRepository.findById(1L)).thenReturn(Optional.of(approvedIntern));
            when(identityServiceClient.getAllUsers()).thenReturn(List.of(mentorUserMap));
            when(internMentorAssignmentRepository.findByInternIdAndStatus(1L, MentorAssignmentStatus.ACTIVE))
                    .thenReturn(Optional.of(oldAssignment));
            when(internProfileRepository.save(any(InternProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

            InternResponse response = internProfileService.assignMentor(1L, request, "HR-Admin");

            assertThat(oldAssignment.getStatus()).isEqualTo(MentorAssignmentStatus.REPLACED);
            assertThat(oldAssignment.getRevocationReason()).isEqualTo("Mentor cũ đổi bộ phận");
            assertThat(response.getMentorId()).isEqualTo(20L);
            assertThat(response.getMentorName()).isEqualTo("Nguyễn Mentor");

            verify(internMentorAssignmentRepository).save(oldAssignment);
            verify(internMentorAssignmentRepository, times(2)).save(any(InternMentorAssignment.class));
        }
    }

    @Nested
    @DisplayName("Tests cho chức năng Revoke Mentor")
    class RevokeMentorTests {

        @Test
        @DisplayName("Thu hồi mentor thành công: gỡ mentor, set status REVOKED và bật cờ needsMentorReassignment")
        void revokeMentor_Success() {
            approvedIntern.setMentorId(20L);
            approvedIntern.setMentorName("Nguyễn Mentor");
            approvedIntern.setStatus(InternStatus.INTERNING);

            InternMentorAssignment activeAssignment = InternMentorAssignment.builder()
                    .intern(approvedIntern)
                    .mentorId(20L)
                    .status(MentorAssignmentStatus.ACTIVE)
                    .build();

            RevokeMentorRequest request = RevokeMentorRequest.builder()
                    .reason("Mentor bận dự án đột xuất")
                    .build();

            when(internProfileRepository.findById(1L)).thenReturn(Optional.of(approvedIntern));
            when(internMentorAssignmentRepository.findByInternIdAndStatus(1L, MentorAssignmentStatus.ACTIVE))
                    .thenReturn(Optional.of(activeAssignment));
            when(internProfileRepository.save(any(InternProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

            InternResponse response = internProfileService.revokeMentor(1L, request, "HR-Admin");

            assertThat(response.getMentorId()).isNull();
            assertThat(response.getMentorName()).isNull();
            assertThat(response.getNeedsMentorReassignment()).isTrue();
            assertThat(response.getMentorReassignmentReason()).isEqualTo("Mentor bận dự án đột xuất");
            // Theo đúng spec mục 2.3: Giữ nguyên trạng thái status chính của TTS (INTERNING) và bật cờ cảnh báo needsMentorReassignment = true
            assertThat(response.getStatus()).isEqualTo(InternStatus.INTERNING);

            assertThat(activeAssignment.getStatus()).isEqualTo(MentorAssignmentStatus.REVOKED);
            assertThat(activeAssignment.getRevocationReason()).isEqualTo("Mentor bận dự án đột xuất");
        }

        @Test
        @DisplayName("Thu hồi mentor thất bại khi thực tập sinh chưa có mentor")
        void revokeMentor_ThrowsException_WhenInternHasNoMentor() {
            approvedIntern.setMentorId(null);
            RevokeMentorRequest request = RevokeMentorRequest.builder().reason("Lý do").build();

            when(internProfileRepository.findById(1L)).thenReturn(Optional.of(approvedIntern));

            assertThatThrownBy(() -> internProfileService.revokeMentor(1L, request, "HR-Admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("chưa được phân công Mentor");
        }
    }
}
