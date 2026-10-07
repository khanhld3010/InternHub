package org.example.internservice.leave.service.impl;

import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.intern.client.NotificationEventDispatcher;
import org.example.internservice.intern.client.dto.CreateNotificationInternalRequest;
import org.example.internservice.intern.entity.InternMentorAssignment;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.intern.entity.enums.InternStatus;
import org.example.internservice.intern.entity.enums.MentorAssignmentStatus;
import org.example.internservice.intern.repository.InternMentorAssignmentRepository;
import org.example.internservice.intern.repository.InternProfileRepository;
import org.example.internservice.leave.dto.request.ApproveLeaveRequest;
import org.example.internservice.leave.dto.request.CreateLeaveRequest;
import org.example.internservice.leave.dto.request.RejectLeaveRequest;
import org.example.internservice.leave.dto.response.LeaveRequestResponse;
import org.example.internservice.leave.dto.response.LeaveRequestSummaryResponse;
import org.example.internservice.leave.entity.LeaveRequest;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.example.internservice.leave.entity.enums.LeaveType;
import org.example.internservice.leave.repository.LeaveRequestRepository;
import org.example.internservice.leave.util.WorkdayCalculator;
import org.example.internservice.security.CustomUserDetails;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceImplTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private InternProfileRepository internProfileRepository;

    @Mock
    private InternMentorAssignmentRepository internMentorAssignmentRepository;

    @Mock
    private WorkdayCalculator workdayCalculator;

    @Mock
    private NotificationEventDispatcher notificationEventDispatcher;

    @InjectMocks
    private LeaveRequestServiceImpl leaveRequestService;

    private InternProfile intern;
    private CustomUserDetails internUserDetails;
    private CustomUserDetails mentorUserDetails;

    @BeforeEach
    void setUp() {
        intern = InternProfile.builder()
                .userId(101L)
                .internCode("INT-2026-001")
                .fullName("Nguyễn Văn An")
                .email("an.nguyen@example.com")
                .phone("0987654321")
                .status(InternStatus.INTERNING)
                .mentorId(202L)
                .build();
        intern.setId(1L);

        internUserDetails = CustomUserDetails.builder()
                .userId(101L)
                .username("an.nguyen")
                .role("ROLE_INTERN")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_INTERN")))
                .build();

        mentorUserDetails = CustomUserDetails.builder()
                .userId(202L)
                .username("mentor.mai")
                .role("ROLE_MENTOR")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))
                .build();
    }

    @Test
    @DisplayName("Nộp đơn xin nghỉ phép thành công khi dữ liệu hợp lệ")
    void createLeaveRequest_Success_WhenValid() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(3);

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .leaveType(LeaveType.ACADEMIC_EXAM)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(start)
                .endDate(end)
                .reason("Em xin nghỉ thi tốt nghiệp môn Toán giải tích.")
                .build();

        when(internProfileRepository.findByUserId(101L)).thenReturn(Optional.of(intern));
        when(workdayCalculator.calculateWorkdays(start, end, LeaveDurationType.FULL_DAY)).thenReturn(2.0);
        when(leaveRequestRepository.existsOverlappingActiveRequest(1L, start, end)).thenReturn(false);

        LeaveRequest saved = LeaveRequest.builder()
                .intern(intern)
                .leaveType(LeaveType.ACADEMIC_EXAM)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(start)
                .endDate(end)
                .totalDays(2.0)
                .reason("Em xin nghỉ thi tốt nghiệp...")
                .status(LeaveStatus.PENDING)
                .build();
        saved.setId(10L);

        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(saved);

        LeaveRequestResponse response = leaveRequestService.createLeaveRequest(101L, request);

        assertNotNull(response);
        assertEquals(LeaveStatus.PENDING, response.getStatus());
        assertEquals(2.0, response.getTotalDays());
        verify(leaveRequestRepository).save(any(LeaveRequest.class));
        verify(notificationEventDispatcher).dispatch(any(CreateNotificationInternalRequest.class));
    }

    @Test
    @DisplayName("Nộp đơn bị từ chối với DuplicateResourceException khi đã có đơn trùng ngày")
    void createLeaveRequest_ThrowDuplicate_WhenOverlappingRequestExists() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(3);

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .leaveType(LeaveType.PERSONAL)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(start)
                .endDate(end)
                .reason("Em xin nghỉ giải quyết việc gia đình.")
                .build();

        when(internProfileRepository.findByUserId(101L)).thenReturn(Optional.of(intern));
        when(workdayCalculator.calculateWorkdays(start, end, LeaveDurationType.FULL_DAY)).thenReturn(2.0);
        when(leaveRequestRepository.existsOverlappingActiveRequest(1L, start, end)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () ->
                leaveRequestService.createLeaveRequest(101L, request)
        );

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nộp đơn việc riêng với ngày bắt đầu trong quá khứ ném BadRequestException")
    void createLeaveRequest_ThrowBadRequest_WhenStartDateInPast() {
        LocalDate pastDate = LocalDate.now().minusDays(2);

        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .leaveType(LeaveType.PERSONAL)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(pastDate)
                .endDate(pastDate)
                .reason("Em xin nghỉ việc riêng hôm trước.")
                .build();

        when(internProfileRepository.findByUserId(101L)).thenReturn(Optional.of(intern));

        assertThrows(BadRequestException.class, () ->
                leaveRequestService.createLeaveRequest(101L, request)
        );
    }

    @Test
    @DisplayName("Hủy đơn thành công khi đơn đang ở trạng thái PENDING và thuộc về user đang đăng nhập")
    void cancelLeaveRequest_Success_WhenPendingAndOwner() {
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .intern(intern)
                .leaveType(LeaveType.PERSONAL)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .totalDays(2.0)
                .reason("Em xin nghỉ việc riêng.")
                .status(LeaveStatus.PENDING)
                .build();
        leaveRequest.setId(10L);

        when(leaveRequestRepository.findByIdWithIntern(10L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));

        LeaveRequestResponse response = leaveRequestService.cancelLeaveRequest(101L, 10L);

        assertNotNull(response);
        assertEquals(LeaveStatus.CANCELLED, response.getStatus());
        assertNotNull(response.getCancelledAt());
    }

    @Test
    @DisplayName("Hủy đơn ném AccessDeniedException khi tài khoản khác cố tình can thiệp (Chống IDOR)")
    void cancelLeaveRequest_ThrowAccessDenied_WhenNotOwner() {
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .intern(intern) // intern có userId = 101L
                .leaveType(LeaveType.PERSONAL)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .totalDays(2.0)
                .reason("Em xin nghỉ việc riêng.")
                .status(LeaveStatus.PENDING)
                .build();
        leaveRequest.setId(10L);

        when(leaveRequestRepository.findByIdWithIntern(10L)).thenReturn(Optional.of(leaveRequest));

        // User khác với userId = 999L cố tình gọi hủy đơn
        assertThrows(AccessDeniedException.class, () ->
                leaveRequestService.cancelLeaveRequest(999L, 10L)
        );
    }

    @Test
    @DisplayName("Hủy đơn ném IllegalStateException khi đơn đã được APPROVED")
    void cancelLeaveRequest_ThrowIllegalState_WhenAlreadyApproved() {
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .intern(intern)
                .leaveType(LeaveType.PERSONAL)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .totalDays(2.0)
                .reason("Em xin nghỉ việc riêng.")
                .status(LeaveStatus.APPROVED)
                .build();
        leaveRequest.setId(10L);

        when(leaveRequestRepository.findByIdWithIntern(10L)).thenReturn(Optional.of(leaveRequest));

        assertThrows(IllegalStateException.class, () ->
                leaveRequestService.cancelLeaveRequest(101L, 10L)
        );
    }

    @Test
    @DisplayName("Mentor duyệt đơn thành công và chuyển trạng thái sang APPROVED")
    void approveLeaveRequest_Success_WhenApproverIsMentor() {
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .intern(intern)
                .leaveType(LeaveType.ACADEMIC_EXAM)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .totalDays(2.0)
                .reason("Em xin nghỉ thi tốt nghiệp.")
                .status(LeaveStatus.PENDING)
                .build();
        leaveRequest.setId(10L);

        InternMentorAssignment assignment = InternMentorAssignment.builder()
                .intern(intern)
                .mentorId(202L)
                .status(MentorAssignmentStatus.ACTIVE)
                .build();

        when(leaveRequestRepository.findByIdWithIntern(10L)).thenReturn(Optional.of(leaveRequest));
        when(internMentorAssignmentRepository.findByInternIdAndStatus(1L, MentorAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(assignment));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));

        ApproveLeaveRequest request = ApproveLeaveRequest.builder()
                .approvalNote("Đồng ý cho em nghỉ.")
                .build();

        LeaveRequestResponse response = leaveRequestService.approveLeaveRequest(mentorUserDetails, 10L, request);

        assertNotNull(response);
        assertEquals(LeaveStatus.APPROVED, response.getStatus());
        assertEquals("mentor.mai", response.getApproverName());
        assertEquals("Đồng ý cho em nghỉ.", response.getApprovalNote());
        verify(notificationEventDispatcher).dispatch(any(CreateNotificationInternalRequest.class));
    }

    @Test
    @DisplayName("Mentor từ chối đơn thành công và ghi nhận lý do")
    void rejectLeaveRequest_Success_WhenReasonProvided() {
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .intern(intern)
                .leaveType(LeaveType.PERSONAL)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .totalDays(2.0)
                .reason("Em xin nghỉ việc riêng.")
                .status(LeaveStatus.PENDING)
                .build();
        leaveRequest.setId(10L);

        InternMentorAssignment assignment = InternMentorAssignment.builder()
                .intern(intern)
                .mentorId(202L)
                .status(MentorAssignmentStatus.ACTIVE)
                .build();

        when(leaveRequestRepository.findByIdWithIntern(10L)).thenReturn(Optional.of(leaveRequest));
        when(internMentorAssignmentRepository.findByInternIdAndStatus(1L, MentorAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(assignment));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));

        RejectLeaveRequest request = RejectLeaveRequest.builder()
                .rejectionReason("Trùng lịch release sản phẩm.")
                .build();

        LeaveRequestResponse response = leaveRequestService.rejectLeaveRequest(mentorUserDetails, 10L, request);

        assertNotNull(response);
        assertEquals(LeaveStatus.REJECTED, response.getStatus());
        assertEquals("Trùng lịch release sản phẩm.", response.getRejectionReason());
        verify(notificationEventDispatcher).dispatch(any(CreateNotificationInternalRequest.class));
    }

    @Test
    @DisplayName("Lấy danh sách đơn nghỉ phép của bản thân trả về PageResponse")
    void getMyLeaveRequests_Success_ReturnPageResponse() {
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .intern(intern)
                .leaveType(LeaveType.SICK)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now())
                .totalDays(1.0)
                .reason("Em bị sốt xuất huyết.")
                .status(LeaveStatus.APPROVED)
                .build();
        leaveRequest.setId(10L);

        Page<LeaveRequest> page = new PageImpl<>(List.of(leaveRequest));
        Pageable pageable = PageRequest.of(0, 10);

        when(internProfileRepository.findByUserId(101L)).thenReturn(Optional.of(intern));
        when(leaveRequestRepository.findMyRequests(1L, null, null, pageable)).thenReturn(page);

        PageResponse<LeaveRequestSummaryResponse> result = leaveRequestService.getMyLeaveRequests(101L, null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(10L, result.getItems().get(0).getId());
    }
}
