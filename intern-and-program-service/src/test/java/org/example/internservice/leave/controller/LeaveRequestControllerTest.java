package org.example.internservice.leave.controller;

import org.example.internservice.common.dto.response.ApiResponse;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.leave.dto.request.ApproveLeaveRequest;
import org.example.internservice.leave.dto.request.CreateLeaveRequest;
import org.example.internservice.leave.dto.request.RejectLeaveRequest;
import org.example.internservice.leave.dto.response.LeaveRequestResponse;
import org.example.internservice.leave.dto.response.LeaveRequestSummaryResponse;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.example.internservice.leave.entity.enums.LeaveType;
import org.example.internservice.leave.service.LeaveRequestService;
import org.example.internservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestControllerTest {

    @Mock
    private LeaveRequestService leaveRequestService;

    @InjectMocks
    private LeaveRequestController leaveRequestController;

    private Authentication internAuth;
    private Authentication mentorAuth;

    @BeforeEach
    void setUp() {
        CustomUserDetails internDetails = CustomUserDetails.builder()
                .userId(101L)
                .username("intern_an")
                .role("INTERN")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERN")))
                .build();
        internAuth = new UsernamePasswordAuthenticationToken(internDetails, null, internDetails.getAuthorities());

        CustomUserDetails mentorDetails = CustomUserDetails.builder()
                .userId(202L)
                .username("mentor_mai")
                .role("MENTOR")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_MENTOR")))
                .build();
        mentorAuth = new UsernamePasswordAuthenticationToken(mentorDetails, null, mentorDetails.getAuthorities());
    }

    @Test
    @DisplayName("POST /api/v1/leave-requests - Trả về HTTP 201 Created khi nộp đơn thành công")
    void createLeaveRequest_Return201Created() {
        CreateLeaveRequest request = CreateLeaveRequest.builder()
                .leaveType(LeaveType.ACADEMIC_EXAM)
                .durationType(LeaveDurationType.FULL_DAY)
                .startDate(LocalDate.now().plusDays(2))
                .endDate(LocalDate.now().plusDays(3))
                .reason("Em xin nghỉ thi tốt nghiệp.")
                .build();

        LeaveRequestResponse mockResponse = LeaveRequestResponse.builder()
                .id(1L)
                .internId(10L)
                .status(LeaveStatus.PENDING)
                .totalDays(2.0)
                .build();

        when(leaveRequestService.createLeaveRequest(eq(101L), any(CreateLeaveRequest.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<LeaveRequestResponse>> response = leaveRequestController.createLeaveRequest(request, internAuth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(201, response.getBody().getCode());
        assertEquals(1L, response.getBody().getData().getId());
        verify(leaveRequestService).createLeaveRequest(eq(101L), any(CreateLeaveRequest.class));
    }

    @Test
    @DisplayName("GET /api/v1/leave-requests/my-requests - Trả về HTTP 200 OK kèm danh sách đơn")
    void getMyLeaveRequests_Return200Ok() {
        PageResponse<LeaveRequestSummaryResponse> mockPage = PageResponse.<LeaveRequestSummaryResponse>builder()
                .items(List.of(LeaveRequestSummaryResponse.builder().id(1L).status(LeaveStatus.PENDING).build()))
                .currentPage(0)
                .pageSize(10)
                .totalItems(1)
                .totalPages(1)
                .build();

        when(leaveRequestService.getMyLeaveRequests(eq(101L), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(mockPage);

        ResponseEntity<ApiResponse<PageResponse<LeaveRequestSummaryResponse>>> response =
                leaveRequestController.getMyLeaveRequests(null, null, PageRequest.of(0, 10), internAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getData().getItems().size());
    }

    @Test
    @DisplayName("PATCH /api/v1/leave-requests/{id}/cancel - Trả về HTTP 200 OK khi hủy đơn thành công")
    void cancelLeaveRequest_Return200Ok() {
        LeaveRequestResponse mockResponse = LeaveRequestResponse.builder()
                .id(1L)
                .status(LeaveStatus.CANCELLED)
                .build();

        when(leaveRequestService.cancelLeaveRequest(101L, 1L)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<LeaveRequestResponse>> response =
                leaveRequestController.cancelLeaveRequest(1L, internAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(LeaveStatus.CANCELLED, response.getBody().getData().getStatus());
    }

    @Test
    @DisplayName("PATCH /api/v1/leave-requests/{id}/approve - Trả về HTTP 200 OK khi duyệt đơn thành công")
    void approveLeaveRequest_Return200Ok() {
        ApproveLeaveRequest request = ApproveLeaveRequest.builder()
                .approvalNote("Đồng ý.")
                .build();

        LeaveRequestResponse mockResponse = LeaveRequestResponse.builder()
                .id(1L)
                .status(LeaveStatus.APPROVED)
                .approverName("mentor_mai")
                .build();

        when(leaveRequestService.approveLeaveRequest(any(), eq(1L), any())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<LeaveRequestResponse>> response =
                leaveRequestController.approveLeaveRequest(1L, request, mentorAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(LeaveStatus.APPROVED, response.getBody().getData().getStatus());
    }

    @Test
    @DisplayName("PATCH /api/v1/leave-requests/{id}/reject - Trả về HTTP 200 OK khi từ chối đơn thành công")
    void rejectLeaveRequest_Return200Ok() {
        RejectLeaveRequest request = RejectLeaveRequest.builder()
                .rejectionReason("Bận họp toàn dự án.")
                .build();

        LeaveRequestResponse mockResponse = LeaveRequestResponse.builder()
                .id(1L)
                .status(LeaveStatus.REJECTED)
                .approverName("mentor_mai")
                .build();

        when(leaveRequestService.rejectLeaveRequest(any(), eq(1L), any())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<LeaveRequestResponse>> response =
                leaveRequestController.rejectLeaveRequest(1L, request, mentorAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(LeaveStatus.REJECTED, response.getBody().getData().getStatus());
    }
}
