package org.example.internservice.leave.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.example.internservice.leave.entity.enums.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Thực thể lưu trữ Đơn xin nghỉ phép của thực tập sinh (TM-28).
 */
@Entity
@Table(
        name = "leave_requests",
        indexes = {
                @Index(name = "idx_leave_request_intern", columnList = "intern_id"),
                @Index(name = "idx_leave_request_status", columnList = "status"),
                @Index(name = "idx_leave_request_dates", columnList = "start_date, end_date"),
                @Index(name = "idx_leave_request_approver", columnList = "approver_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternProfile intern;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 30)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_type", nullable = false, length = 20)
    private LeaveDurationType durationType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false)
    private Double totalDays;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approver_name", length = 100)
    private String approverName;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "approval_note", columnDefinition = "TEXT")
    private String approvalNote;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public void cancel() {
        if (!this.status.canTransitionTo(LeaveStatus.CANCELLED)) {
            throw new IllegalStateException("Không thể hủy đơn nghỉ phép đang ở trạng thái: " + this.status.getDescription());
        }
        this.status = LeaveStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void approve(Long approverId, String approverName, String approvalNote) {
        if (!this.status.canTransitionTo(LeaveStatus.APPROVED)) {
            throw new IllegalStateException("Không thể phê duyệt đơn nghỉ phép đang ở trạng thái: " + this.status.getDescription());
        }
        this.status = LeaveStatus.APPROVED;
        this.approverId = approverId;
        this.approverName = approverName;
        this.approvedAt = LocalDateTime.now();
        this.approvalNote = approvalNote;
        this.rejectionReason = null;
    }

    public void reject(Long approverId, String approverName, String rejectionReason) {
        if (!this.status.canTransitionTo(LeaveStatus.REJECTED)) {
            throw new IllegalStateException("Không thể từ chối đơn nghỉ phép đang ở trạng thái: " + this.status.getDescription());
        }
        this.status = LeaveStatus.REJECTED;
        this.approverId = approverId;
        this.approverName = approverName;
        this.approvedAt = LocalDateTime.now();
        this.rejectionReason = rejectionReason;
        this.approvalNote = null;
    }
}
