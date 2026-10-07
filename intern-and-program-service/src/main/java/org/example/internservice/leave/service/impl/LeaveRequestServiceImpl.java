package org.example.internservice.leave.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.DuplicateResourceException;
import org.example.internservice.exception.ResourceNotFoundException;
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
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.example.internservice.leave.entity.enums.LeaveType;
import org.example.internservice.leave.repository.LeaveRequestRepository;
import org.example.internservice.leave.service.LeaveRequestService;
import org.example.internservice.leave.util.WorkdayCalculator;
import org.example.internservice.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LeaveRequestServiceImpl implements LeaveRequestService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LeaveRequestRepository leaveRequestRepository;
    private final InternProfileRepository internProfileRepository;
    private final InternMentorAssignmentRepository internMentorAssignmentRepository;
    private final WorkdayCalculator workdayCalculator;
    private final NotificationEventDispatcher notificationEventDispatcher;

    @Override
    @Transactional
    public LeaveRequestResponse createLeaveRequest(Long userId, CreateLeaveRequest request) {
        InternProfile intern = getValidInternProfile(userId);
        LocalDate today = LocalDate.now(VIETNAM_ZONE);

        // Kiểm tra hạn nộp trước (Lead time)
        if (request.getLeaveType() == LeaveType.SICK || request.getLeaveType() == LeaveType.BEREAVEMENT) {
            if (request.getStartDate().isBefore(today.minusDays(1))) {
                throw new BadRequestException("Đơn nghỉ ốm hoặc việc gia đình đột xuất chỉ được nộp bù trong vòng 24 giờ kể từ ngày nghỉ");
            }
        } else {
            if (request.getStartDate().isBefore(today)) {
                throw new BadRequestException("Ngày bắt đầu xin nghỉ phép không được ở trong quá khứ");
            }
        }

        // Tính toán số ngày làm việc thực tế
        double totalDays = workdayCalculator.calculateWorkdays(request.getStartDate(), request.getEndDate(), request.getDurationType());

        // Kiểm tra chống trùng lặp thời gian nghỉ
        if (leaveRequestRepository.existsOverlappingActiveRequest(intern.getId(), request.getStartDate(), request.getEndDate())) {
            throw new DuplicateResourceException("Bạn đã có đơn xin nghỉ phép khác đang chờ duyệt hoặc đã được phê duyệt trong khoảng thời gian này");
        }

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .intern(intern)
                .leaveType(request.getLeaveType())
                .durationType(request.getDurationType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalDays(totalDays)
                .reason(request.getReason().trim())
                .attachmentUrl(request.getAttachmentUrl())
                .status(LeaveStatus.PENDING)
                .build();

        LeaveRequest savedRequest = leaveRequestRepository.save(leaveRequest);
        log.info("Intern ID {} (User ID {}) đã tạo đơn nghỉ phép ID {} thành công", intern.getId(), userId, savedRequest.getId());

        // Bắn thông báo cho Mentor phụ trách
        notifyMentorOnNewRequest(intern, savedRequest, userId);

        return mapToResponse(savedRequest);
    }

    @Override
    @Transactional
    public LeaveRequestResponse cancelLeaveRequest(Long userId, Long leaveRequestId) {
        LeaveRequest leaveRequest = getLeaveRequestWithIntern(leaveRequestId);

        if (!leaveRequest.getIntern().getUserId().equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn xin nghỉ phép của người khác");
        }

        leaveRequest.cancel();
        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);
        log.info("Intern User ID {} đã hủy đơn xin nghỉ phép ID {}", userId, leaveRequestId);

        return mapToResponse(updated);
    }

    @Override
    public PageResponse<LeaveRequestSummaryResponse> getMyLeaveRequests(Long userId, LeaveStatus status, Integer year, Pageable pageable) {
        InternProfile intern = getValidInternProfile(userId);
        Page<LeaveRequest> page = leaveRequestRepository.findMyRequests(intern.getId(), status, year, pageable);
        return PageResponse.from(page, this::mapToSummaryResponse);
    }

    @Override
    public LeaveRequestResponse getLeaveRequestDetail(Long userId, List<String> roles, Long leaveRequestId) {
        LeaveRequest leaveRequest = getLeaveRequestWithIntern(leaveRequestId);

        boolean isHrOrAdmin = roles.stream().anyMatch(r -> r.contains("HR") || r.contains("ADMIN"));
        if (!isHrOrAdmin) {
            boolean isOwner = leaveRequest.getIntern().getUserId().equals(userId);
            boolean isMentor = isMentorAssignedToIntern(userId, leaveRequest.getIntern().getId());
            if (!isOwner && !isMentor) {
                throw new AccessDeniedException("Bạn không có quyền xem chi tiết đơn xin nghỉ phép này");
            }
        }

        return mapToResponse(leaveRequest);
    }

    @Override
    public PageResponse<LeaveRequestSummaryResponse> getPendingRequests(CustomUserDetails userDetails, Pageable pageable) {
        boolean isHrOrAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("HR") || a.getAuthority().contains("ADMIN"));

        if (isHrOrAdmin) {
            Page<LeaveRequest> allPending = leaveRequestRepository.findAllPending(pageable);
            return PageResponse.from(allPending, this::mapToSummaryResponse);
        }

        List<Long> internIds = internMentorAssignmentRepository.findByMentorIdAndStatus(userDetails.getUserId(), MentorAssignmentStatus.ACTIVE)
                .stream()
                .map(assignment -> assignment.getIntern().getId())
                .toList();

        if (internIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable), this::mapToSummaryResponse);
        }

        Page<LeaveRequest> pendingForMentor = leaveRequestRepository.findPendingByInternIds(internIds, pageable);
        return PageResponse.from(pendingForMentor, this::mapToSummaryResponse);
    }

    @Override
    @Transactional
    public LeaveRequestResponse approveLeaveRequest(CustomUserDetails approverDetails, Long leaveRequestId, ApproveLeaveRequest request) {
        LeaveRequest leaveRequest = getLeaveRequestWithIntern(leaveRequestId);
        verifyApproverAuthority(approverDetails, leaveRequest);

        leaveRequest.approve(
                approverDetails.getUserId(),
                approverDetails.getUsername(),
                request != null ? request.getApprovalNote() : null
        );

        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);
        log.info("User {} đã phê duyệt đơn nghỉ phép ID {}", approverDetails.getUsername(), leaveRequestId);

        // Bắn thông báo kết quả cho Intern
        notificationEventDispatcher.dispatch(CreateNotificationInternalRequest.builder()
                .recipientId(leaveRequest.getIntern().getUserId())
                .actorId(approverDetails.getUserId())
                .title("Đơn xin nghỉ phép đã được phê duyệt")
                .content(String.format("Đơn xin nghỉ phép từ ngày %s đến ngày %s đã được phê duyệt bởi %s.",
                        leaveRequest.getStartDate(), leaveRequest.getEndDate(), approverDetails.getUsername()))
                .type("LEAVE_REQUEST_APPROVED")
                .referenceType("LEAVE_REQUEST")
                .referenceId(String.valueOf(leaveRequest.getId()))
                .actionUrl("/intern/leave-requests/" + leaveRequest.getId())
                .build());

        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public LeaveRequestResponse rejectLeaveRequest(CustomUserDetails approverDetails, Long leaveRequestId, RejectLeaveRequest request) {
        LeaveRequest leaveRequest = getLeaveRequestWithIntern(leaveRequestId);
        verifyApproverAuthority(approverDetails, leaveRequest);

        leaveRequest.reject(
                approverDetails.getUserId(),
                approverDetails.getUsername(),
                request.getRejectionReason().trim()
        );

        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);
        log.info("User {} đã từ chối đơn nghỉ phép ID {}", approverDetails.getUsername(), leaveRequestId);

        // Bắn thông báo kết quả cho Intern
        notificationEventDispatcher.dispatch(CreateNotificationInternalRequest.builder()
                .recipientId(leaveRequest.getIntern().getUserId())
                .actorId(approverDetails.getUserId())
                .title("Đơn xin nghỉ phép bị từ chối")
                .content(String.format("Đơn xin nghỉ phép từ ngày %s đến ngày %s đã bị từ chối. Lý do: %s",
                        leaveRequest.getStartDate(), leaveRequest.getEndDate(), request.getRejectionReason()))
                .type("LEAVE_REQUEST_REJECTED")
                .referenceType("LEAVE_REQUEST")
                .referenceId(String.valueOf(leaveRequest.getId()))
                .actionUrl("/intern/leave-requests/" + leaveRequest.getId())
                .build());

        return mapToResponse(updated);
    }

    private InternProfile getValidInternProfile(Long userId) {
        InternProfile intern = internProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ thực tập sinh liên kết với tài khoản này"));

        if (intern.getStatus() != InternStatus.INTERNING && intern.getStatus() != InternStatus.APPROVED) {
            throw new BadRequestException("Hồ sơ thực tập sinh không ở trạng thái hoạt động hợp lệ để thực hiện thao tác");
        }
        return intern;
    }

    private LeaveRequest getLeaveRequestWithIntern(Long id) {
        return leaveRequestRepository.findByIdWithIntern(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn xin nghỉ phép với mã ID: " + id));
    }

    private boolean isMentorAssignedToIntern(Long mentorUserId, Long internId) {
        return internMentorAssignmentRepository.findByInternIdAndStatus(internId, MentorAssignmentStatus.ACTIVE)
                .map(a -> a.getMentorId().equals(mentorUserId))
                .orElse(false);
    }

    private void verifyApproverAuthority(CustomUserDetails approverDetails, LeaveRequest leaveRequest) {
        boolean isHrOrAdmin = approverDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("HR") || a.getAuthority().contains("ADMIN"));

        if (!isHrOrAdmin) {
            boolean isMentor = isMentorAssignedToIntern(approverDetails.getUserId(), leaveRequest.getIntern().getId());
            if (!isMentor) {
                throw new AccessDeniedException("Bạn không phải Mentor phụ trách thực tập sinh này, không có quyền duyệt/từ chối đơn");
            }
        }
    }

    private void notifyMentorOnNewRequest(InternProfile intern, LeaveRequest request, Long internUserId) {
        Long mentorUserId = intern.getMentorId();
        if (mentorUserId == null) {
            mentorUserId = internMentorAssignmentRepository.findByInternIdAndStatus(intern.getId(), MentorAssignmentStatus.ACTIVE)
                    .map(InternMentorAssignment::getMentorId)
                    .orElse(null);
        }

        if (mentorUserId != null) {
            notificationEventDispatcher.dispatch(CreateNotificationInternalRequest.builder()
                    .recipientId(mentorUserId)
                    .actorId(internUserId)
                    .title("Đơn xin nghỉ phép mới cần xét duyệt")
                    .content(String.format("Thực tập sinh %s đã nộp đơn xin nghỉ phép (%s) từ %s đến %s.",
                            intern.getFullName(), request.getLeaveType().getDescription(), request.getStartDate(), request.getEndDate()))
                    .type("LEAVE_REQUEST_SUBMITTED")
                    .referenceType("LEAVE_REQUEST")
                    .referenceId(String.valueOf(request.getId()))
                    .actionUrl("/mentor/leave-requests/" + request.getId())
                    .build());
        }
    }

    private LeaveRequestResponse mapToResponse(LeaveRequest l) {
        return LeaveRequestResponse.builder()
                .id(l.getId())
                .internId(l.getIntern().getId())
                .internCode(l.getIntern().getInternCode())
                .internName(l.getIntern().getFullName())
                .internEmail(l.getIntern().getEmail())
                .internPhone(l.getIntern().getPhone())
                .leaveType(l.getLeaveType())
                .leaveTypeDescription(l.getLeaveType() != null ? l.getLeaveType().getDescription() : null)
                .durationType(l.getDurationType())
                .durationTypeDescription(l.getDurationType() != null ? l.getDurationType().getDescription() : null)
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .totalDays(l.getTotalDays())
                .reason(l.getReason())
                .attachmentUrl(l.getAttachmentUrl())
                .status(l.getStatus())
                .statusDescription(l.getStatus() != null ? l.getStatus().getDescription() : null)
                .approverId(l.getApproverId())
                .approverName(l.getApproverName())
                .approvedAt(l.getApprovedAt())
                .rejectionReason(l.getRejectionReason())
                .approvalNote(l.getApprovalNote())
                .cancelledAt(l.getCancelledAt())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }

    private LeaveRequestSummaryResponse mapToSummaryResponse(LeaveRequest l) {
        return LeaveRequestSummaryResponse.builder()
                .id(l.getId())
                .internId(l.getIntern().getId())
                .internCode(l.getIntern().getInternCode())
                .internName(l.getIntern().getFullName())
                .leaveType(l.getLeaveType())
                .leaveTypeDescription(l.getLeaveType() != null ? l.getLeaveType().getDescription() : null)
                .durationType(l.getDurationType())
                .durationTypeDescription(l.getDurationType() != null ? l.getDurationType().getDescription() : null)
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .totalDays(l.getTotalDays())
                .reason(l.getReason())
                .status(l.getStatus())
                .statusDescription(l.getStatus() != null ? l.getStatus().getDescription() : null)
                .approverName(l.getApproverName())
                .approvedAt(l.getApprovedAt())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
