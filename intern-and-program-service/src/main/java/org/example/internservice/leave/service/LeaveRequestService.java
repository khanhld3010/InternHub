package org.example.internservice.leave.service;

import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.leave.dto.request.ApproveLeaveRequest;
import org.example.internservice.leave.dto.request.CreateLeaveRequest;
import org.example.internservice.leave.dto.request.RejectLeaveRequest;
import org.example.internservice.leave.dto.response.LeaveRequestResponse;
import org.example.internservice.leave.dto.response.LeaveRequestSummaryResponse;
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LeaveRequestService {

    /**
     * Thực tập sinh nộp đơn xin nghỉ phép mới (TM-28).
     */
    LeaveRequestResponse createLeaveRequest(Long userId, CreateLeaveRequest request);

    /**
     * Thực tập sinh hủy đơn xin nghỉ phép khi đang PENDING.
     */
    LeaveRequestResponse cancelLeaveRequest(Long userId, Long leaveRequestId);

    /**
     * Tra cứu lịch sử đơn xin nghỉ phép cá nhân của thực tập sinh.
     */
    PageResponse<LeaveRequestSummaryResponse> getMyLeaveRequests(Long userId, LeaveStatus status, Integer year, Pageable pageable);

    /**
     * Xem chi tiết một đơn xin nghỉ phép (hỗ trợ kiểm tra quyền IDOR).
     */
    LeaveRequestResponse getLeaveRequestDetail(Long userId, List<String> roles, Long leaveRequestId);

    /**
     * Mentor / HR lấy danh sách các đơn đang chờ xét duyệt (PENDING).
     */
    PageResponse<LeaveRequestSummaryResponse> getPendingRequests(CustomUserDetails userDetails, Pageable pageable);

    /**
     * Mentor / HR phê duyệt đơn xin nghỉ phép.
     */
    LeaveRequestResponse approveLeaveRequest(CustomUserDetails approverDetails, Long leaveRequestId, ApproveLeaveRequest request);

    /**
     * Mentor / HR từ chối đơn xin nghỉ phép.
     */
    LeaveRequestResponse rejectLeaveRequest(CustomUserDetails approverDetails, Long leaveRequestId, RejectLeaveRequest request);
}
