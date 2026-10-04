package org.example.internservice.mission.service;

import org.example.internservice.exception.BadRequestException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.MentorProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.intern.repository.MentorProfileRepository;
import org.example.internservice.mission.dto.request.CreateMissionItemRequest;
import org.example.internservice.mission.dto.request.UpdateItemStatusRequest;
import org.example.internservice.mission.dto.request.UpdateMissionItemRequest;
import org.example.internservice.mission.dto.response.MissionItemResponse;
import org.example.internservice.mission.entity.MissionBoard;
import org.example.internservice.mission.entity.MissionItem;
import org.example.internservice.mission.entity.enums.BoardStatus;
import org.example.internservice.mission.entity.enums.MissionItemStatus;
import org.example.internservice.mission.entity.enums.MissionPriority;
import org.example.internservice.mission.repository.MissionBoardRepository;
import org.example.internservice.mission.repository.MissionItemRepository;
import org.example.internservice.mission.service.impl.MissionItemServiceImpl;
import org.example.internservice.program.entity.Department;
import org.example.internservice.program.entity.InternshipProgram;
import org.example.internservice.program.entity.enums.ProgramStatus;
import org.example.internservice.program.repository.ProgramMentorRepository;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionItemServiceTest {

    @Mock
    private MissionItemRepository missionItemRepository;

    @Mock
    private MissionBoardRepository missionBoardRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private ProgramMentorRepository programMentorRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @InjectMocks
    private MissionItemServiceImpl missionItemService;

    private CustomUserDetails mentorUserDetails;
    private InternshipProgram mockProgram;
    private MentorProfile mockMentor;
    private MissionBoard mockBoard;
    private InternProfile mockIntern1;
    private InternProfile mockIntern2;

    @BeforeEach
    void setUp() {
        mentorUserDetails = CustomUserDetails.builder()
                .userId(100L)
                .username("mentor1")
                .role("ROLE_MENTOR")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))
                .build();

        Department dept = Department.builder().code("DEV").name("Phòng Dev").build();
        dept.setId(1L);

        mockProgram = InternshipProgram.builder()
                .programCode("PROG-2026-001")
                .name("Chương trình Java Backend K28")
                .department(dept)
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
                .department(dept)
                .status("ACTIVE")
                .build();
        mockMentor.setId(20L);

        mockBoard = MissionBoard.builder()
                .program(mockProgram)
                .mentor(mockMentor)
                .title("Giai đoạn 1: Onboarding")
                .status(BoardStatus.ACTIVE)
                .build();
        mockBoard.setId(1L);

        mockIntern1 = InternProfile.builder()
                .internCode("INT-001")
                .fullName("Nguyễn Văn A")
                .email("a@example.com")
                .program(mockProgram)
                .status(InternStatus.INTERNING)
                .build();
        mockIntern1.setId(11L);

        mockIntern2 = InternProfile.builder()
                .internCode("INT-002")
                .fullName("Trần Thị B")
                .email("b@example.com")
                .program(mockProgram)
                .status(InternStatus.INTERNING)
                .build();
        mockIntern2.setId(12L);
    }

    @Test
    @DisplayName("UT-BE-08: Tạo mục công việc chi tiết thành công gán cho nhiều TTS")
    void createItem_Success_MultipleAssignees() {
        CreateMissionItemRequest request = CreateMissionItemRequest.builder()
                .title("Cài đặt Docker và chạy thử Discovery Server")
                .description("Chạy port 8761")
                .priority(MissionPriority.HIGH)
                .dueDate(LocalDate.now().plusDays(5))
                .internIds(Set.of(11L, 12L))
                .build();

        MissionItem mockItem = MissionItem.builder()
                .board(mockBoard)
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(MissionPriority.HIGH)
                .status(MissionItemStatus.TODO)
                .dueDate(request.getDueDate())
                .assignees(Set.of(mockIntern1, mockIntern2))
                .build();
        mockItem.setId(101L);
        mockItem.setCreatedAt(LocalDateTime.now());
        mockItem.setUpdatedAt(LocalDateTime.now());

        when(missionBoardRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockBoard));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);
        when(internProfileRepository.findAllById(any())).thenReturn(List.of(mockIntern1, mockIntern2));
        when(missionItemRepository.save(any(MissionItem.class))).thenReturn(mockItem);

        MissionItemResponse response = missionItemService.createItem(1L, request, mentorUserDetails);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("TODO", response.getStatus().name());
        assertEquals(2, response.getAssignees().size());
        verify(missionItemRepository, times(1)).save(any(MissionItem.class));
    }

    @Test
    @DisplayName("UT-BE-09: Bị từ chối khi chọn TTS không thuộc Program của Board")
    void createItem_Fail_InternNotInProgram() {
        InternshipProgram otherProgram = InternshipProgram.builder().name("Khóa khác").build();
        otherProgram.setId(99L);

        InternProfile otherIntern = InternProfile.builder()
                .internCode("INT-999")
                .fullName("Người lạ")
                .program(otherProgram)
                .build();
        otherIntern.setId(99L);

        CreateMissionItemRequest request = CreateMissionItemRequest.builder()
                .title("Nhiệm vụ")
                .internIds(Set.of(99L))
                .build();

        when(missionBoardRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockBoard));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);
        when(internProfileRepository.findAllById(any())).thenReturn(List.of(otherIntern));

        assertThrows(BadRequestException.class, () ->
                missionItemService.createItem(1L, request, mentorUserDetails));
        verify(missionItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-BE-10: Cập nhật trạng thái mục công việc thành công (TODO -> IN_PROGRESS -> COMPLETED)")
    void updateItemStatus_Success() {
        MissionItem item = MissionItem.builder()
                .board(mockBoard)
                .title("Viết API")
                .status(MissionItemStatus.TODO)
                .assignees(new HashSet<>())
                .build();
        item.setId(101L);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        UpdateItemStatusRequest request = UpdateItemStatusRequest.builder()
                .status(MissionItemStatus.COMPLETED)
                .build();

        when(missionItemRepository.findByIdWithAssignees(101L)).thenReturn(Optional.of(item));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);
        when(missionItemRepository.save(any(MissionItem.class))).thenReturn(item);

        MissionItemResponse response = missionItemService.updateItemStatus(101L, request, mentorUserDetails);

        assertNotNull(response);
        assertEquals(MissionItemStatus.COMPLETED, response.getStatus());
        verify(missionItemRepository, times(1)).save(item);
    }

    @Test
    @DisplayName("UT-BE-11: Bị từ chối khi TTS không ở trạng thái INTERNING hoặc APPROVED")
    void createItem_Fail_InternStatusInvalid() {
        InternProfile inactiveIntern = InternProfile.builder()
                .internCode("INT-003")
                .fullName("Lê Văn C")
                .program(mockProgram)
                .status(InternStatus.TERMINATED)
                .build();
        inactiveIntern.setId(13L);

        CreateMissionItemRequest request = CreateMissionItemRequest.builder()
                .title("Nhiệm vụ mới")
                .internIds(Set.of(13L))
                .build();

        when(missionBoardRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockBoard));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);
        when(internProfileRepository.findAllById(any())).thenReturn(List.of(inactiveIntern));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                missionItemService.createItem(1L, request, mentorUserDetails));
        assertTrue(ex.getMessage().contains("không thể giao việc"));
        verify(missionItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-BE-12: Bị từ chối khi thời hạn dueDate ở trong quá khứ")
    void createItem_Fail_DueDateInPast() {
        CreateMissionItemRequest request = CreateMissionItemRequest.builder()
                .title("Nhiệm vụ hạn chót quá khứ")
                .dueDate(LocalDate.now().minusDays(1))
                .internIds(Set.of(11L))
                .build();

        when(missionBoardRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(mockBoard));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                missionItemService.createItem(1L, request, mentorUserDetails));
        assertTrue(ex.getMessage().contains("quá khứ"));
        verify(missionItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-BE-13: Bị từ chối khi cập nhật mục công việc với danh sách internIds rỗng")
    void updateItem_Fail_EmptyAssignees() {
        MissionItem item = MissionItem.builder()
                .board(mockBoard)
                .title("Viết API")
                .status(MissionItemStatus.TODO)
                .assignees(new HashSet<>(Set.of(mockIntern1)))
                .build();
        item.setId(101L);

        UpdateMissionItemRequest request = UpdateMissionItemRequest.builder()
                .internIds(Set.of())
                .build();

        when(missionItemRepository.findByIdWithAssignees(101L)).thenReturn(Optional.of(item));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                missionItemService.updateItem(101L, request, mentorUserDetails));
        assertTrue(ex.getMessage().contains("Vui lòng chọn ít nhất 1 thực tập sinh tham gia công việc"));
        verify(missionItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-BE-14: Bị từ chối khi cập nhật mục công việc với dueDate ở trong quá khứ")
    void updateItem_Fail_DueDateInPast() {
        MissionItem item = MissionItem.builder()
                .board(mockBoard)
                .title("Viết API")
                .status(MissionItemStatus.TODO)
                .assignees(new HashSet<>(Set.of(mockIntern1)))
                .build();
        item.setId(101L);

        UpdateMissionItemRequest request = UpdateMissionItemRequest.builder()
                .dueDate(LocalDate.now().minusDays(2))
                .build();

        when(missionItemRepository.findByIdWithAssignees(101L)).thenReturn(Optional.of(item));
        when(mentorProfileRepository.findByUserId(100L)).thenReturn(Optional.of(mockMentor));
        when(programMentorRepository.existsByProgramIdAndMentorIdentifier(10L, 20L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                missionItemService.updateItem(101L, request, mentorUserDetails));
        assertTrue(ex.getMessage().contains("quá khứ"));
        verify(missionItemRepository, never()).save(any());
    }
}
