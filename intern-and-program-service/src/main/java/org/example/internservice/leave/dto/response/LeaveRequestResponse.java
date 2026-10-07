package org.example.internservice.leave.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.example.internservice.leave.entity.enums.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestResponse {

    private Long id;

    // Thông tin thực tập sinh
    private Long internId;
    private String internCode;
    private String internName;
    private String internEmail;
    private String internPhone;

    // Chi tiết nghỉ phép
    private LeaveType leaveType;
    private String leaveTypeDescription;
    private LeaveDurationType durationType;
    private String durationTypeDescription;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalDays;
    private String reason;
    private String attachmentUrl;
    private LeaveStatus status;
    private String statusDescription;

    // Thông tin phê duyệt / từ chối / hủy
    private Long approverId;
    private String approverName;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private String approvalNote;
    private LocalDateTime cancelledAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
