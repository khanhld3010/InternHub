package org.example.internservice.allowance.service;

import org.example.internservice.allowance.dto.request.AdjustAllowanceRequest;
import org.example.internservice.allowance.dto.request.BatchApproveAllowanceRequest;
import org.example.internservice.allowance.dto.request.UnlockPeriodRequest;
import org.example.internservice.allowance.dto.response.BatchApproveResultResponse;
import org.example.internservice.allowance.entity.InternAllowanceAuditLog;
import org.example.internservice.allowance.entity.InternAllowanceRecord;
import org.example.internservice.allowance.entity.enums.AllowanceCalculationType;
import org.example.internservice.allowance.entity.enums.AllowanceStatus;
import org.example.internservice.allowance.repository.InternAllowanceAuditLogRepository;
import org.example.internservice.allowance.repository.InternAllowanceRecordRepository;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.example.internservice.contract.repository.DynamicContractRepository;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.leave.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TC-ALW-SVC: Allowance Service Business Rules Unit Tests")
class AllowanceServiceBusinessTest {

    @Mock
    private InternAllowanceRecordRepository allowanceRepository;
    @Mock
    private InternAllowanceAuditLogRepository auditLogRepository;
    @Mock
    private DynamicContractRepository contractRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AllowanceServiceImpl allowanceService;

    private InternAllowanceRecord sampleRecord;

    @BeforeEach
    void setUp() {
        sampleRecord = InternAllowanceRecord.builder()
                .internUserId(10L)
                .periodMonth("2026-10")
                .status(AllowanceStatus.DRAFT)
                .baseAllowance(new BigDecimal("5000000"))
                .actualWorkingDays(22)
                .standardWorkingDays(22)
                .paidLeaveDays(0)
                .bonusAmount(BigDecimal.ZERO)
                .deductionAmount(BigDecimal.ZERO)
                .retroAdjustmentAmount(BigDecimal.ZERO)
                .actualAmount(new BigDecimal("5000000"))
                .calculationType(AllowanceCalculationType.MONTHLY)
                .isManuallyAdjusted(false)
                .createdBy(1L)
                .build();
        sampleRecord.setId(100L);
    }

    @Test
    @DisplayName("TC-ALW-04: Batch Approve chuyển trạng thái từ DRAFT sang APPROVED")
    void testBatchApprove_DraftRecords_Success() {
        when(allowanceRepository.findAllById(List.of(100L))).thenReturn(List.of(sampleRecord));
        when(allowanceRepository.save(any(InternAllowanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        BatchApproveAllowanceRequest request = BatchApproveAllowanceRequest.builder()
                .periodMonth("2026-10")
                .recordIds(List.of(100L))
                .build();

        BatchApproveResultResponse result = allowanceService.batchApprove(request, 999L);

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(AllowanceStatus.APPROVED, sampleRecord.getStatus());
        verify(allowanceRepository, times(1)).save(sampleRecord);
    }

    @Test
    @DisplayName("TC-ALW-05: Mở khóa kỳ - Chỉ kỳ APPROVED (chưa PAID) mới được mở khóa về DRAFT")
    void testUnlockPeriod_ApprovedToDraft_Success() {
        sampleRecord.setStatus(AllowanceStatus.APPROVED);
        when(allowanceRepository.findByPeriodMonth("2026-10")).thenReturn(List.of(sampleRecord));
        when(allowanceRepository.save(any(InternAllowanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        UnlockPeriodRequest req = new UnlockPeriodRequest();
        req.setReason("Phát hiện chấm công thiếu của ngày 15/10 cần cập nhật lại");

        allowanceService.unlockPeriod("2026-10", req, 999L);

        assertEquals(AllowanceStatus.DRAFT, sampleRecord.getStatus(), "Kỳ phải được chuyển về DRAFT");
        verify(allowanceRepository, atLeastOnce()).save(sampleRecord);
    }

    @Test
    @DisplayName("TC-ALW-06: Mở khóa kỳ - Kỳ đã PAID tuyệt đối bị đóng băng, không được mở khóa")
    void testUnlockPeriod_PaidPeriod_ThrowsBadRequest() {
        sampleRecord.setStatus(AllowanceStatus.PAID);
        when(allowanceRepository.findByPeriodMonth("2026-10")).thenReturn(List.of(sampleRecord));

        UnlockPeriodRequest req = new UnlockPeriodRequest();
        req.setReason("Muốn sửa lại dữ liệu đã thanh toán");

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                allowanceService.unlockPeriod("2026-10", req, 999L)
        );

        assertTrue(ex.getMessage().contains("PAID") || ex.getMessage().contains("thanh toán"),
                "Không được mở khóa kỳ đã chuyển tiền PAID");
    }

    @Test
    @DisplayName("TC-ALW-07: Điều chỉnh vượt quá công chuẩn sẽ ném BadRequestException")
    void testAdjustAllowance_ExceedStandardWorkingDays_ThrowsBadRequest() {
        when(allowanceRepository.findById(100L)).thenReturn(Optional.of(sampleRecord));

        AdjustAllowanceRequest request = AdjustAllowanceRequest.builder()
                .actualWorkingDays(25) // Chuẩn là 22
                .paidLeaveDays(0)
                .bonusAmount(BigDecimal.ZERO)
                .deductionAmount(BigDecimal.ZERO)
                .adjustmentReason("Cố tình nhập quá công chuẩn")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                allowanceService.adjustAllowance(100L, request, 999L, "HR User")
        );

        assertTrue(ex.getMessage().contains("vượt quá số ngày công chuẩn"),
                "Phải chặn việc nhập tổng ngày làm việc vượt quá ngày công chuẩn");
    }
}
