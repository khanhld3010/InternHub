package org.example.internservice.mission;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.mission.dto.request.CreateMissionItemRequest;
import org.example.internservice.mission.dto.response.AssigneeResponse;
import org.example.internservice.mission.dto.response.MentorProgramResponse;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.mission.entity.MissionBoard;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.BoardStatus;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.mission.repository.MissionBoardRepository;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.example.internservice.mission.service.impl.MissionBoardServiceImpl;
import org.example.internservice.mission.service.impl.MissionItemServiceImpl;
import org.example.internservice.program.dto.request.CreateGroupRequest;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternGroup;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.ProgramMentor;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.InternGroupRepository;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.repository.ProgramMentorRepository;
import org.example.internservice.program.service.impl.InternGroupServiceImpl;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorWorkspaceEnhancementTest {

    @Mock
    private MissionBoardRepository missionBoardRepository;

    @Mock
    private MissionItemRepository missionItemRepository;

    @Mock
    private InternshipProgramRepository programRepository;

    @Mock
    private ProgramMentorRepository programMentorRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternGroupRepository internGroupRepository;

    @InjectMocks
    private MissionBoardServiceImpl missionBoardService;

    @InjectMocks
    private MissionItemServiceImpl missionItemService;

    @InjectMocks
    private InternGroupServiceImpl internGroupService;

    private CustomUserDetails mentorUserDetails;
    private InternshipProgram mockProgram;
    private MentorProfile mockMentor;
    private MissionBoard mockBoard;

    @BeforeEach
    void setUp() {
        mentorUserDetails = CustomUserDetails.builder()
                .userId(100L)
                .username("mentor1")
                .role("ROLE_MENTOR")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))
                .build();

        Department department = Department.builder()
                .code("DEV")
                .name("Phòng Phát triển Phần mềm")
                .build();
        department.setId(1L);

        mockProgram = InternshipProgram.builder()
                .programCode("PROG-2026-001")
                .name("Chương trình Java Backend K28")
                .department(department)
                .status(ProgramStatus.ONGOING)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(2))
                .build();
        mockProgram.setId(10L);

        mockMentor = MentorProfile.builder()
                .userId(100L)
                .fullName("Nguyễn Văn Mentor")
                .email("mentor1@internhub.vn")
                .phone("0912345678")
                .department(department)
                .status("ACTIVE")
                .build();
        mockMentor.setId(5L);

        mockBoard = MissionBoard.builder()
                .program(mockProgram)
                .mentor(mockMentor)
                .title("Bảng nhiệm vụ Sprint 1")
                .description("Sprint 1")
                .status(BoardStatus.ACTIVE)
                .build();
        mockBoard.setId(50L);
    }

    @Test
    @DisplayName("UT-BE-01: getMyMentoredPrograms - Trả về đầy đủ groupCount, mentorCount, totalTaskCount, completedTaskCount, progressPercent")
    void test_getMyMentoredPrograms_withEnrichedBatchStats() {
        // Arrange
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));

        ProgramMentor pm = ProgramMentor.builder()
                .program(mockProgram)
                .mentor(mockMentor)
                .build();
        when(programMentorRepository.findByMentorIdentifierWithProgram(5L)).thenReturn(List.of(pm));
        when(internProfileRepository.findByProgramId(10L)).thenReturn(List.of());
        when(internProfileRepository.countByProgramIdAndStatusIn(eq(10L), any())).thenReturn(0L);

        // Mock aggregate counts:
        List<Object[]> groupsList = new ArrayList<>();
        groupsList.add(new Object[]{10L, 3L});
        when(internGroupRepository.countGroupsByProgramIds(any())).thenReturn(groupsList);

        List<Object[]> mentorsList = new ArrayList<>();
        mentorsList.add(new Object[]{10L, 2L});
        when(programMentorRepository.countMentorsByProgramIds(any())).thenReturn(mentorsList);

        List<Object[]> tasksList = new ArrayList<>();
        tasksList.add(new Object[]{10L, 10L, 6L});
        when(missionItemRepository.countTasksByProgramIds(any())).thenReturn(tasksList);

        // Act
        List<MentorProgramResponse> result = missionBoardService.getMyMentoredPrograms(mentorUserDetails);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        MentorProgramResponse programResp = result.get(0);
        assertEquals(10L, programResp.getProgramId());
        assertEquals(3, programResp.getGroupCount());
        assertEquals(2, programResp.getMentorCount());
        assertEquals(10, programResp.getTotalTaskCount());
        assertEquals(6, programResp.getCompletedTaskCount());
        assertEquals(60.0, programResp.getProgressPercent());
    }

    @Test
    @DisplayName("UT-BE-02: getProgramInterns - Trả về danh sách TTS kèm thông tin nhóm và số task đang làm (workload)")
    void test_getProgramInterns_withGroupAndWorkload() {
        // Arrange
        when(programRepository.findById(10L)).thenReturn(Optional.of(mockProgram));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 5L)).thenReturn(true);

        InternGroup group = InternGroup.builder()
                .name("Nhóm 01 - Thanh Toán")
                .build();
        group.setId(8L);

        InternProfile intern1 = InternProfile.builder()
                .internCode("INT-001")
                .fullName("Trần Thị A")
                .status(InternStatus.INTERNING)
                .group(group)
                .build();
        intern1.setId(201L);

        InternProfile intern2 = InternProfile.builder()
                .internCode("INT-002")
                .fullName("Lê Văn B")
                .status(InternStatus.INTERNING)
                .group(null)
                .build();
        intern2.setId(202L);

        when(internProfileRepository.findByProgramId(10L)).thenReturn(List.of(intern1, intern2));

        // Grouped by intern: intern1 có 3 active, 2 completed; intern2 có 0 active, 1 completed
        List<Object[]> internTasks = new ArrayList<>();
        internTasks.add(new Object[]{201L, 3L, 2L});
        internTasks.add(new Object[]{202L, 0L, 1L});
        when(missionItemRepository.countTasksByProgramIdGroupedByIntern(10L)).thenReturn(internTasks);

        // Act
        List<AssigneeResponse> response = missionBoardService.getProgramInterns(10L, mentorUserDetails);

        // Assert
        assertEquals(2, response.size());

        AssigneeResponse resp1 = response.stream().filter(r -> r.getId().equals(201L)).findFirst().orElseThrow();
        assertEquals(8L, resp1.getGroupId());
        assertEquals("Nhóm 01 - Thanh Toán", resp1.getGroupName());
        assertEquals(3, resp1.getActiveTaskCount());
        assertEquals(2, resp1.getCompletedTaskCount());

        AssigneeResponse resp2 = response.stream().filter(r -> r.getId().equals(202L)).findFirst().orElseThrow();
        assertNull(resp2.getGroupId());
        assertNull(resp2.getGroupName());
        assertEquals(0, resp2.getActiveTaskCount());
        assertEquals(1, resp2.getCompletedTaskCount());
    }

    @Test
    @DisplayName("UT-BE-03: createItem - Hạn chót dueDate vượt quá ngày kết thúc chương trình ném BadRequestException")
    void test_createItem_dueDateExceedsProgramEndDate_throwsBadRequest() {
        // Arrange
        when(missionBoardRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(mockBoard));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 5L)).thenReturn(true);

        LocalDate invalidDueDate = mockProgram.getEndDate().plusDays(5);
        CreateMissionItemRequest request = CreateMissionItemRequest.builder()
                .title("Công việc trễ hạn chương trình")
                .dueDate(invalidDueDate)
                .internIds(Set.of(201L))
                .build();

        // Act & Assert
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                missionItemService.createItem(50L, request, mentorUserDetails)
        );
        assertTrue(ex.getMessage().contains("không được vượt quá ngày kết thúc chương trình"));
    }

    @Test
    @DisplayName("UT-BE-04: createItem - Smart Fallback cho phép Mentor khi có TTS trong kỳ gán cho mình")
    void test_createItem_smartFallbackAccess_success() {
        // Arrange
        when(missionBoardRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(mockBoard));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        // Mentor không có trong program_mentors
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 5L)).thenReturn(false);
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 100L)).thenReturn(false);

        // Smart fallback: Tìm thấy intern gán mentorId = 5L
        InternProfile internInProgram = InternProfile.builder()
                .internCode("INT-001")
                .fullName("Trần Thị A")
                .mentorId(5L)
                .status(InternStatus.INTERNING)
                .program(mockProgram)
                .build();
        internInProgram.setId(201L);

        when(internProfileRepository.findByProgramId(10L)).thenReturn(List.of(internInProgram));
        when(internProfileRepository.findAllById(any())).thenReturn(List.of(internInProgram));

        MissionItem savedItem = MissionItem.builder()
                .board(mockBoard)
                .title("Công việc Smart Fallback")
                .priority(MissionPriority.MEDIUM)
                .status(MissionItemStatus.TODO)
                .dueDate(LocalDate.now().plusDays(2))
                .assignees(Set.of(internInProgram))
                .build();
        savedItem.setId(99L);
        when(missionItemRepository.save(any(MissionItem.class))).thenReturn(savedItem);

        CreateMissionItemRequest request = CreateMissionItemRequest.builder()
                .title("Công việc Smart Fallback")
                .dueDate(LocalDate.now().plusDays(2))
                .internIds(Set.of(201L))
                .build();

        // Act
        MissionItemResponse response = missionItemService.createItem(50L, request, mentorUserDetails);

        // Assert
        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals(MissionItemStatus.TODO, response.getStatus());
    }

    @Test
    @DisplayName("UT-BE-05: internGroup - Mentor lạ không được phân công và không có TTS trong kỳ bị ném AccessDeniedException")
    void test_internGroup_verifyAccess_unassignedMentor_throwsAccessDenied() {
        // Set Authentication trong SecurityContext
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                mentorUserDetails, null, mentorUserDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
            when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 5L)).thenReturn(false);
            when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 100L)).thenReturn(false);
            when(internProfileRepository.findByProgramId(10L)).thenReturn(Collections.emptyList());

            CreateGroupRequest request = CreateGroupRequest.builder()
                    .name("Nhóm Trái Phép")
                    .maxMembers(4)
                    .build();

            // Act & Assert
            assertThrows(AccessDeniedException.class, () ->
                    internGroupService.createGroup(10L, request)
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
