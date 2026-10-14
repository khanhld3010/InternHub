package org.example.internservice.mission.service;

import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.mission.dto.request.CreateMissionBoardRequest;
import org.example.internservice.mission.dto.request.UpdateMissionBoardRequest;
import org.example.internservice.mission.dto.response.MissionBoardDetailResponse;
import org.example.internservice.mission.dto.response.MissionBoardResponse;
import org.example.internservice.mission.entity.MissionBoard;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.BoardStatus;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.mission.repository.MissionBoardRepository;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.example.internservice.mission.service.impl.MissionBoardServiceImpl;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.InternshipProgramRepository;
import org.example.internservice.program.repository.ProgramMentorRepository;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionBoardServiceTest {

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
    private org.example.internservice.program.repository.DepartmentRepository departmentRepository;

    @Mock
    private org.example.internservice.intern.client.IdentityServiceClient identityServiceClient;

    @Mock
    private org.example.internservice.program.repository.InternGroupRepository internGroupRepository;

    @InjectMocks
    private MissionBoardServiceImpl missionBoardService;

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
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(ProgramStatus.ONGOING)
                .build();
        mockProgram.setId(10L);

        mockMentor = MentorProfile.builder()
                .userId(100L)
                .fullName("Vũ Thị Thu Hà")
                .email("ha.vu@example.com")
                .phone("0987654321")
                .department(department)
                .status("ACTIVE")
                .build();
        mockMentor.setId(20L);

        mockBoard = MissionBoard.builder()
                .program(mockProgram)
                .mentor(mockMentor)
                .title("Giai đoạn 1: Onboarding")
                .description("Thiết lập môi trường làm việc")
                .status(BoardStatus.ACTIVE)
                .items(Collections.emptyList())
                .build();
        mockBoard.setId(1L);
        mockBoard.setCreatedAt(LocalDateTime.now());
        mockBoard.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("UT-BE-01: Tạo MissionBoard thành công khi Mentor được phân công vào Program")
    void createBoard_Success() {
        CreateMissionBoardRequest request = CreateMissionBoardRequest.builder()
                .title("Giai đoạn 1: Onboarding")
                .description("Thiết lập môi trường làm việc")
                .build();

        when(programRepository.findById(10L)).thenReturn(Optional.of(mockProgram));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);
        when(missionBoardRepository.save(any(MissionBoard.class))).thenReturn(mockBoard);

        MissionBoardResponse response = missionBoardService.createBoard(10L, request, mentorUserDetails);

        assertNotNull(response);
        assertEquals("Giai đoạn 1: Onboarding", response.getTitle());
        assertEquals(10L, response.getProgramId());
        assertEquals(20L, response.getMentorId());
        verify(missionBoardRepository, times(1)).save(any(MissionBoard.class));
    }

    @Test
    @DisplayName("UT-BE-02: Bị từ chối (AccessDeniedException) khi Mentor không được phân công vào Program")
    void createBoard_Fail_NotMentorOfProgram() {
        CreateMissionBoardRequest request = CreateMissionBoardRequest.builder()
                .title("Giai đoạn 1: Onboarding")
                .build();

        when(programRepository.findById(10L)).thenReturn(Optional.of(mockProgram));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                missionBoardService.createBoard(10L, request, mentorUserDetails));
        verify(missionBoardRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-BE-03: Lấy danh sách MissionBoard của Program thành công kèm thống kê số lượng")
    void getBoardsByProgram_Success() {
        when(programRepository.findById(10L)).thenReturn(Optional.of(mockProgram));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);
        when(missionBoardRepository.findByProgramIdWithDetails(10L)).thenReturn(List.of(mockBoard));
        when(missionItemRepository.countByBoardIdAndStatus(1L, MissionItemStatus.TODO)).thenReturn(2L);
        when(missionItemRepository.countByBoardIdAndStatus(1L, MissionItemStatus.IN_PROGRESS)).thenReturn(1L);
        when(missionItemRepository.countByBoardIdAndStatus(1L, MissionItemStatus.COMPLETED)).thenReturn(3L);

        List<MissionBoardResponse> list = missionBoardService.getBoardsByProgram(10L, mentorUserDetails);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(6, list.get(0).getTotalItems());
        assertEquals(2, list.get(0).getTodoCount());
        assertEquals(1, list.get(0).getInProgressCount());
        assertEquals(3, list.get(0).getCompletedCount());
    }

    @Test
    @DisplayName("UT-BE-04: Lấy chi tiết MissionBoard phân chia chuẩn xác theo 3 cột Kanban")
    void getBoardDetail_Success() {
        MissionItem itemTodo = MissionItem.builder()
                .board(mockBoard)
                .title("Cài đặt Git")
                .status(MissionItemStatus.TODO)
                .priority(MissionPriority.HIGH)
                .assignees(new HashSet<>())
                .build();
        itemTodo.setId(101L);

        MissionItem itemDone = MissionItem.builder()
                .board(mockBoard)
                .title("Đọc tài liệu Onboarding")
                .status(MissionItemStatus.COMPLETED)
                .priority(MissionPriority.MEDIUM)
                .assignees(new HashSet<>())
                .build();
        itemDone.setId(102L);

        when(missionBoardRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockBoard));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);
        when(missionItemRepository.findByBoardIdWithAssignees(1L)).thenReturn(List.of(itemTodo, itemDone));

        MissionBoardDetailResponse detail = missionBoardService.getBoardDetail(1L, mentorUserDetails);

        assertNotNull(detail);
        assertEquals(1, detail.getTodoItems().size());
        assertEquals(0, detail.getInProgressItems().size());
        assertEquals(1, detail.getCompletedItems().size());
        assertEquals(2, detail.getTotalItems());
    }

    @Test
    @DisplayName("UT-BE-05: Cập nhật thông tin MissionBoard thành công")
    void updateBoard_Success() {
        UpdateMissionBoardRequest request = UpdateMissionBoardRequest.builder()
                .title("Giai đoạn 1: Onboarding (Cập nhật)")
                .description("Cập nhật mô tả mới")
                .build();

        when(missionBoardRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockBoard));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);
        when(missionBoardRepository.save(any(MissionBoard.class))).thenReturn(mockBoard);

        MissionBoardResponse response = missionBoardService.updateBoard(1L, request, mentorUserDetails);

        assertNotNull(response);
        verify(missionBoardRepository, times(1)).save(any(MissionBoard.class));
    }

    @Test
    @DisplayName("UT-BE-06: Xóa MissionBoard thành công")
    void deleteBoard_Success() {
        when(missionBoardRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockBoard));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);

        missionBoardService.deleteBoard(1L, mentorUserDetails);

        verify(missionBoardRepository, times(1)).delete(mockBoard);
    }

    @Test
    @DisplayName("UT-BE-07: Lấy danh sách Program mà Mentor phụ trách")
    void getMyMentoredPrograms_Success() {
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.findByMentorIdentifierWithProgram(20L)).thenReturn(Collections.emptyList());

        var list = missionBoardService.getMyMentoredPrograms(mentorUserDetails);

        assertNotNull(list);
        verify(programMentorRepository, times(1)).findByMentorIdentifierWithProgram(20L);
    }
}
