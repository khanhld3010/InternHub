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
public class LeaveRequestSummaryResponse {

    private Long id;
    private Long internId;
    private String internCode;
    private String internName;
    private LeaveType leaveType;
    private String leaveTypeDescription;
    private LeaveDurationType durationType;
    private String durationTypeDescription;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalDays;
    private String reason;
    private LeaveStatus status;
    private String statusDescription;
    private String approverName;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
