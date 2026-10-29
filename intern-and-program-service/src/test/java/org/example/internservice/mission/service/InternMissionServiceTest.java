package org.example.internservice.mission.service;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.mission.dto.request.UpdateKanbanStatusRequest;
import org.example.internservice.mission.dto.response.InternKanbanBoardResponse;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.mission.entity.MissionBoard;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.BoardStatus;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.example.internservice.mission.service.impl.InternMissionServiceImpl;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternMissionServiceTest {

    @Mock
    private MissionItemRepository missionItemRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private NotificationEventDispatcher notificationEventDispatcher;

    @InjectMocks
    private InternMissionServiceImpl internMissionService;

    private CustomUserDetails internUserDetails;
    private CustomUserDetails otherInternUserDetails;
    private InternProfile internProfile;
    private InternProfile otherInternProfile;
    private InternshipProgram program;
    private MentorProfile mentor;
    private MissionBoard board;
    private MissionItem missionItemTodo;
    private MissionItem missionItemInProgress;

    @BeforeEach
    void setUp() {
        internUserDetails = CustomUserDetails.builder()
                .userId(100L)
                .username("intern.an@example.com")
                .role("ROLE_INTERN")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_INTERN")))
                .build();

        otherInternUserDetails = CustomUserDetails.builder()
                .userId(200L)
                .username("intern.binh@example.com")
                .role("ROLE_INTERN")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_INTERN")))
                .build();

        Department department = Department.builder()
                .name("Kỹ thuật Phần mềm")
                .code("SE")
                .build();
        department.setId(1L);

        program = InternshipProgram.builder()
                .programCode("PROG-2026-001")
                .name("Chương trình Thực tập Java 2026")
                .status(ProgramStatus.ONGOING)
                .department(department)
                .startDate(LocalDate.now().minusDays(10))
                .endDate(LocalDate.now().plusMonths(2))
                .build();
        program.setId(10L);

        mentor = MentorProfile.builder()
                .userId(50L)
                .fullName("Mentor Minh")
                .email("mentor.minh@example.com")
                .phone("0987654321")
                .department(department)
                .status("ACTIVE")
                .build();
        mentor.setId(5L);

        internProfile = InternProfile.builder()
                .userId(100L)
                .internCode("INT-2026-0001")
                .fullName("Nguyễn Văn An")
                .email("intern.an@example.com")
                .phone("0912345678")
                .university("Đại học Bách Khoa")
                .major("CNTT")
                .appliedPosition("Java Backend Intern")
                .startDate(LocalDate.now().minusDays(10))
                .status(InternStatus.INTERNING)
                .program(program)
                .build();
        internProfile.setId(1L);

        otherInternProfile = InternProfile.builder()
                .userId(200L)
                .internCode("INT-2026-0002")
                .fullName("Trần Văn Bình")
                .email("intern.binh@example.com")
                .phone("0988888888")
                .university("Đại học Quốc Gia")
                .major("Khoa học máy tính")
                .appliedPosition("Frontend Intern")
                .startDate(LocalDate.now().minusDays(10))
                .status(InternStatus.INTERNING)
                .program(program)
                .build();
        otherInternProfile.setId(2L);

        board = MissionBoard.builder()
                .program(program)
                .mentor(mentor)
                .title("Bảng Nhiệm Vụ Tuần 1")
                .status(BoardStatus.ACTIVE)
                .build();
        board.setId(20L);

        missionItemTodo = MissionItem.builder()
                .board(board)
                .title("Cài đặt Redis và Docker")
                .description("Cài đặt môi trường phát triển")
                .priority(MissionPriority.HIGH)
                .status(MissionItemStatus.TODO)
                .dueDate(LocalDate.now().plusDays(5))
                .orderIndex(0)
                .assignees(new HashSet<>(Set.of(internProfile)))
                .build();
        missionItemTodo.setId(101L);

        missionItemInProgress = MissionItem.builder()
                .board(board)
                .title("Phát triển REST API")
                .description("Viết controller và service")
                .priority(MissionPriority.MEDIUM)
                .status(MissionItemStatus.IN_PROGRESS)
                .dueDate(LocalDate.now().plusDays(7))
                .orderIndex(1)
                .assignees(new HashSet<>(Set.of(internProfile)))
                .build();
        missionItemInProgress.setId(102L);
    }

    @Test
    @DisplayName("getMyKanbanBoard: Lấy thành công danh sách nhiệm vụ nhóm 3 cột Kanban")
    void getMyKanbanBoard_Success() {
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(internProfile));
        when(missionItemRepository.findAssignedItemsByInternIdWithDetails(1L))
                .thenReturn(List.of(missionItemTodo, missionItemInProgress));

        InternKanbanBoardResponse response = internMissionService.getMyKanbanBoard(internUserDetails);

        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getTodoCount());
        assertEquals(1, response.getInProgressCount());
        assertEquals(0, response.getCompletedCount());
        assertEquals(1, response.getTodoItems().size());
        assertEquals("Cài đặt Redis và Docker", response.getTodoItems().get(0).getTitle());
    }

    @Test
    @DisplayName("getMissionDetail: Lấy chi tiết công việc thành công khi là Assignee")
    void getMissionDetail_Success() {
        when(missionItemRepository.findByIdWithBoardAndAssignees(101L)).thenReturn(Optional.of(missionItemTodo));
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(internProfile));

        MissionItemResponse response = internMissionService.getMissionDetail(101L, internUserDetails);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("Cài đặt Redis và Docker", response.getTitle());
        assertEquals(MissionItemStatus.TODO, response.getStatus());
    }

    @Test
    @DisplayName("getMissionDetail: Ném AccessDeniedException khi TTS không thuộc Assignees")
    void getMissionDetail_Fail_NotAssignee() {
        when(missionItemRepository.findByIdWithBoardAndAssignees(101L)).thenReturn(Optional.of(missionItemTodo));
        when(internProfileRepository.findByUserId(200L)).thenReturn(Optional.of(otherInternProfile));

        assertThrows(AccessDeniedException.class, () ->
                internMissionService.getMissionDetail(101L, otherInternUserDetails));
    }

    @Test
    @DisplayName("updateKanbanStatus: Chuyển trạng thái sang IN_PROGRESS thành công")
    void updateKanbanStatus_Success_ToInProgress() {
        when(missionItemRepository.findByIdWithBoardAndAssignees(101L)).thenReturn(Optional.of(missionItemTodo));
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(internProfile));
        when(missionItemRepository.save(any(MissionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateKanbanStatusRequest request = UpdateKanbanStatusRequest.builder()
                .status(MissionItemStatus.IN_PROGRESS)
                .build();

        MissionItemResponse response = internMissionService.updateKanbanStatus(101L, request, internUserDetails);

        assertNotNull(response);
        assertEquals(MissionItemStatus.IN_PROGRESS, response.getStatus());
        verify(notificationEventDispatcher, times(1)).dispatch(any(CreateNotificationInternalRequest.class));
    }

    @Test
    @DisplayName("updateKanbanStatus: Chuyển trạng thái sang COMPLETED kèm link nộp bài thành công")
    void updateKanbanStatus_Success_ToCompleted_WithSubmission() {
        when(missionItemRepository.findByIdWithBoardAndAssignees(102L)).thenReturn(Optional.of(missionItemInProgress));
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(internProfile));
        when(missionItemRepository.save(any(MissionItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateKanbanStatusRequest request = UpdateKanbanStatusRequest.builder()
                .status(MissionItemStatus.COMPLETED)
                .submissionUrl("https://github.com/example/pull/42")
                .completionNote("Đã hoàn thành các API và viết unit test.")
                .build();

        MissionItemResponse response = internMissionService.updateKanbanStatus(102L, request, internUserDetails);

        assertNotNull(response);
        assertEquals(MissionItemStatus.COMPLETED, response.getStatus());
        assertEquals("https://github.com/example/pull/42", response.getSubmissionUrl());
        assertEquals("Đã hoàn thành các API và viết unit test.", response.getCompletionNote());
        assertNotNull(response.getSubmittedAt());
        verify(notificationEventDispatcher, times(1)).dispatch(any(CreateNotificationInternalRequest.class));
    }

    @Test
    @DisplayName("updateKanbanStatus: Chặn IDOR - Ném AccessDeniedException khi TTS khác cố tình chuyển trạng thái")
    void updateKanbanStatus_Fail_NotAssignee() {
        when(missionItemRepository.findByIdWithBoardAndAssignees(101L)).thenReturn(Optional.of(missionItemTodo));
        when(internProfileRepository.findByUserId(200L)).thenReturn(Optional.of(otherInternProfile));

        UpdateKanbanStatusRequest request = UpdateKanbanStatusRequest.builder()
                .status(MissionItemStatus.IN_PROGRESS)
                .build();

        assertThrows(AccessDeniedException.class, () ->
                internMissionService.updateKanbanStatus(101L, request, otherInternUserDetails));
        verify(missionItemRepository, never()).save(any());
        verify(notificationEventDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("updateKanbanStatus: Ném BadRequestException khi hồ sơ TTS bị TERMINATED")
    void updateKanbanStatus_Fail_InternTerminated() {
        internProfile.setStatus(InternStatus.TERMINATED);
        when(missionItemRepository.findByIdWithBoardAndAssignees(101L)).thenReturn(Optional.of(missionItemTodo));
        when(internProfileRepository.findByUserId(100L)).thenReturn(Optional.of(internProfile));

        UpdateKanbanStatusRequest request = UpdateKanbanStatusRequest.builder()
                .status(MissionItemStatus.IN_PROGRESS)
                .build();

        assertThrows(BadRequestException.class, () ->
                internMissionService.updateKanbanStatus(101L, request, internUserDetails));
    }

    @Test
    @DisplayName("updateKanbanStatus: Ném BadRequestException khi request null hoặc status null")
    void updateKanbanStatus_Fail_NullStatus() {
        assertThrows(BadRequestException.class, () ->
                internMissionService.updateKanbanStatus(101L, null, internUserDetails));

        UpdateKanbanStatusRequest emptyRequest = new UpdateKanbanStatusRequest();
        assertThrows(BadRequestException.class, () ->
                internMissionService.updateKanbanStatus(101L, emptyRequest, internUserDetails));
    }
}
