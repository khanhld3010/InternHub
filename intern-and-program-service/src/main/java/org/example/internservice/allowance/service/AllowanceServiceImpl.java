package org.example.internservice.allowance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.allowance.dto.request.AdjustAllowanceRequest;
import org.example.internservice.allowance.dto.request.BatchApproveAllowanceRequest;
import org.example.internservice.allowance.dto.request.RetroAdjustmentRequest;
import org.example.internservice.allowance.dto.request.UnlockPeriodRequest;
import org.example.internservice.allowance.dto.response.AllowanceAuditLogResponse;
import org.example.internservice.allowance.dto.response.AllowanceRecordResponse;
import org.example.internservice.allowance.dto.response.BatchApproveResultResponse;
import org.example.internservice.allowance.entity.InternAllowanceAuditLog;
import org.example.internservice.allowance.entity.InternAllowanceRecord;
import org.example.internservice.allowance.entity.enums.AllowanceCalculationType;
import org.example.internservice.allowance.entity.enums.AllowanceStatus;
import org.example.internservice.allowance.event.AllowancePaidEvent;
import org.example.internservice.allowance.repository.InternAllowanceAuditLogRepository;
import org.example.internservice.allowance.repository.InternAllowanceRecordRepository;
import org.example.internservice.attendance.entity.Attendance;
import org.example.internservice.attendance.entity.enums.AttendanceStatus;
import org.example.internservice.attendance.repository.AttendanceRepository;
import org.example.internservice.contract.entity.Contract;
import org.example.internservice.contract.entity.enums.DynamicContractStatus;
import org.example.internservice.contract.repository.DynamicContractRepository;
import org.example.internservice.exception.BadRequestException;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.leave.entity.LeaveRequest;
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.example.internservice.leave.repository.LeaveRequestRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AllowanceServiceImpl implements AllowanceService {

    private final InternAllowanceRecordRepository allowanceRepository;
    private final InternAllowanceAuditLogRepository auditLogRepository;
    private final DynamicContractRepository contractRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<AllowanceRecordResponse> calculatePreview(String periodMonth) {
        YearMonth ym = YearMonth.parse(periodMonth);
        int standardWorkingDays = WorkingCalendarHelper.calculateStandardWorkingDays(ym.getYear(), ym.getMonthValue());

        List<Contract> activeContracts = contractRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> c.getStatus() == DynamicContractStatus.ACTIVE || c.getStatus() == DynamicContractStatus.SIGNED)
                .collect(Collectors.toList());

        List<AllowanceRecordResponse> previews = new ArrayList<>();

        for (Contract contract : activeContracts) {
            InternProfile intern = contract.getInternProfile();
            if (intern == null || intern.getUserId() == null) continue;

            // Kiem tra xem ban ghi da ton tai trong DB chua
            Optional<InternAllowanceRecord> existingOpt = allowanceRepository
                    .findByInternUserIdAndPeriodMonthAndContractId(intern.getUserId(), periodMonth, contract.getId());

            if (existingOpt.isPresent()) {
                previews.add(mapToResponse(existingOpt.get()));
                continue;
            }

            // Tinh toan so ngay cong va ngay phep hop le trong khoang hieu luc hop dong
            CalculationResult result = computeDaysAndAmount(intern, contract, ym, standardWorkingDays);

            AllowanceRecordResponse dto = AllowanceRecordResponse.builder()
                    .internUserId(intern.getUserId())
                    .internProfileId(intern.getId())
                    .internFullName(intern.getFullName())
                    .internEmail(intern.getEmail())
                    .contractId(contract.getId())
                    .contractNumber(contract.getContractNumber())
                    .periodMonth(periodMonth)
                    .calculationType(AllowanceCalculationType.MONTHLY)
                    .baseAllowance(contract.getAllowanceAmount() != null ? contract.getAllowanceAmount() : BigDecimal.ZERO)
                    .standardWorkingDays(standardWorkingDays)
                    .autoCalculatedDays(result.actualDays)
                    .autoCalculatedAmount(result.calculatedAmount)
                    .actualWorkingDays(result.actualDays)
                    .paidLeaveDays(result.paidLeaveDays)
                    .bonusAmount(BigDecimal.ZERO)
                    .deductionAmount(BigDecimal.ZERO)
                    .retroAdjustmentAmount(BigDecimal.ZERO)
                    .actualAmount(result.calculatedAmount)
                    .isManuallyAdjusted(false)
                    .status(AllowanceStatus.DRAFT)
                    .isLocked(false)
                    .build();

            previews.add(dto);
        }

        return previews;
    }

    @Override
    @Transactional
    public List<AllowanceRecordResponse> batchSave(String periodMonth, Long hrUserId) {
        YearMonth ym = YearMonth.parse(periodMonth);
        int standardWorkingDays = WorkingCalendarHelper.calculateStandardWorkingDays(ym.getYear(), ym.getMonthValue());

        List<Contract> activeContracts = contractRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> c.getStatus() == DynamicContractStatus.ACTIVE || c.getStatus() == DynamicContractStatus.SIGNED)
                .collect(Collectors.toList());

        List<InternAllowanceRecord> savedRecords = new ArrayList<>();

        for (Contract contract : activeContracts) {
            InternProfile intern = contract.getInternProfile();
            if (intern == null || intern.getUserId() == null) continue;

            Optional<InternAllowanceRecord> existingOpt = allowanceRepository
                    .findByInternUserIdAndPeriodMonthAndContractId(intern.getUserId(), periodMonth, contract.getId());

            if (existingOpt.isPresent()) {
                InternAllowanceRecord existing = existingOpt.get();
                // Neu da khoa hoac da sua tay thi khong ghi de
                if (existing.isLocked() || existing.getIsManuallyAdjusted()) {
                    savedRecords.add(existing);
                    continue;
                }
            }

            CalculationResult result = computeDaysAndAmount(intern, contract, ym, standardWorkingDays);

            InternAllowanceRecord record = existingOpt.orElseGet(() -> InternAllowanceRecord.builder()
                    .internUserId(intern.getUserId())
                    .internProfile(intern)
                    .contract(contract)
                    .periodMonth(periodMonth)
                    .createdBy(hrUserId)
                    .build());

            record.setCalculationType(AllowanceCalculationType.MONTHLY);
            record.setBaseAllowance(contract.getAllowanceAmount() != null ? contract.getAllowanceAmount() : BigDecimal.ZERO);
            record.setStandardWorkingDays(standardWorkingDays);
            record.setAutoCalculatedDays(result.actualDays);
            record.setAutoCalculatedAmount(result.calculatedAmount);
            record.setActualWorkingDays(result.actualDays);
            record.setPaidLeaveDays(result.paidLeaveDays);
            record.setBonusAmount(BigDecimal.ZERO);
            record.setDeductionAmount(BigDecimal.ZERO);
            record.setRetroAdjustmentAmount(BigDecimal.ZERO);
            record.setActualAmount(result.calculatedAmount);
            record.setStatus(AllowanceStatus.DRAFT);
            record.setIsManuallyAdjusted(false);

            savedRecords.add(allowanceRepository.save(record));
        }

        return savedRecords.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AllowanceRecordResponse adjustAllowance(Long id, AdjustAllowanceRequest request, Long hrUserId, String hrUserName) {
        InternAllowanceRecord record = allowanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu phụ cấp ID: " + id));

        if (record.isLocked()) {
            throw new BadRequestException("Kỳ lương đã được duyệt hoặc thanh toán, không thể chỉnh sửa trực tiếp");
        }

        if (request.getActualWorkingDays() + request.getPaidLeaveDays() > record.getStandardWorkingDays()) {
            throw new BadRequestException("Tổng ngày làm việc và ngày nghỉ phép không được vượt quá số ngày công chuẩn ("
                    + record.getStandardWorkingDays() + " ngày)");
        }

        // Tinh toan tien moi theo cong thuc va lam tron 1000d
        BigDecimal base = record.getBaseAllowance();
        BigDecimal dailyRate = base.divide(BigDecimal.valueOf(record.getStandardWorkingDays()), 4, RoundingMode.HALF_UP);
        BigDecimal workingAmount = dailyRate.multiply(BigDecimal.valueOf(request.getActualWorkingDays() + request.getPaidLeaveDays()));
        BigDecimal newTotal = workingAmount
                .add(request.getBonusAmount())
                .subtract(request.getDeductionAmount())
                .add(record.getRetroAdjustmentAmount());

        BigDecimal roundedNewAmount = roundToThousand(newTotal.max(BigDecimal.ZERO));

        // Luu Audit Log append-only
        InternAllowanceAuditLog auditLog = InternAllowanceAuditLog.builder()
                .allowanceRecord(record)
                .previousWorkingDays(record.getActualWorkingDays())
                .previousPaidLeaveDays(record.getPaidLeaveDays())
                .previousBonusAmount(record.getBonusAmount())
                .previousDeductionAmount(record.getDeductionAmount())
                .previousAmount(record.getActualAmount())
                .newWorkingDays(request.getActualWorkingDays())
                .newPaidLeaveDays(request.getPaidLeaveDays())
                .newBonusAmount(request.getBonusAmount())
                .newDeductionAmount(request.getDeductionAmount())
                .newAmount(roundedNewAmount)
                .adjustmentReason(request.getAdjustmentReason())
                .adjustedById(hrUserId)
                .adjustedByName(hrUserName)
                .adjustedAt(LocalDateTime.now())
                .build();
        auditLogRepository.save(auditLog);

        // Cap nhat record hien tai
        record.setActualWorkingDays(request.getActualWorkingDays());
        record.setPaidLeaveDays(request.getPaidLeaveDays());
        record.setBonusAmount(request.getBonusAmount());
        record.setDeductionAmount(request.getDeductionAmount());
        record.setActualAmount(roundedNewAmount);
        record.setIsManuallyAdjusted(true);
        if (request.getNotes() != null) {
            record.setNotes(request.getNotes());
        }

        return mapToResponse(allowanceRepository.save(record));
    }

    @Override
    @Transactional
    public BatchApproveResultResponse batchApprove(BatchApproveAllowanceRequest request, Long approverId) {
        List<InternAllowanceRecord> targets;
        if (request.getRecordIds() != null && !request.getRecordIds().isEmpty()) {
            targets = allowanceRepository.findAllById(request.getRecordIds());
        } else {
            targets = allowanceRepository.findByPeriodMonthAndOptionalStatus(request.getPeriodMonth(), AllowanceStatus.DRAFT);
        }

        int successCount = 0;
        int failedCount = 0;
        List<BatchApproveResultResponse.BatchErrorItem> errors = new ArrayList<>();

        for (InternAllowanceRecord record : targets) {
            try {
                if (record.getStatus() != AllowanceStatus.DRAFT) {
                    throw new BadRequestException("Bản ghi không ở trạng thái DRAFT");
                }
                if (approverId != null && approverId.equals(record.getCreatedBy())) {
                    throw new BadRequestException("Vi phạm Maker-Checker: Người lập phiếu không được tự phê duyệt chi");
                }
                if (record.getActualAmount().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException("Số tiền phụ cấp không được âm");
                }
                record.setStatus(AllowanceStatus.APPROVED);
                allowanceRepository.save(record);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                errors.add(BatchApproveResultResponse.BatchErrorItem.builder()
                        .recordId(record.getId())
                        .internUserId(record.getInternUserId())
                        .internName(record.getInternProfile() != null ? record.getInternProfile().getFullName() : "N/A")
                        .reason(e.getMessage())
                        .build());
            }
        }

        return BatchApproveResultResponse.builder()
                .totalProcessed(targets.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .errors(errors)
                .build();
    }

    @Override
    @Transactional
    public void unlockPeriod(String periodMonth, UnlockPeriodRequest request, Long unlockerId) {
        List<InternAllowanceRecord> records = allowanceRepository.findByPeriodMonth(periodMonth);
        if (records.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy kỳ lương: " + periodMonth);
        }

        boolean hasPaid = records.stream().anyMatch(r -> r.getStatus() == AllowanceStatus.PAID);
        if (hasPaid) {
            throw new BadRequestException("Kỳ lương đã có bản ghi chi trả (PAID), tuyệt đối không được mở khóa");
        }

        for (InternAllowanceRecord r : records) {
            if (r.getStatus() == AllowanceStatus.APPROVED) {
                r.setStatus(AllowanceStatus.DRAFT);
                allowanceRepository.save(r);
            }
        }
        log.info("HR Manager ID: {} da mo khoa ky luong {} ve DRAFT voi ly do: {}", unlockerId, periodMonth, request.getReason());
    }

    @Override
    @Transactional
    public AllowanceRecordResponse markPaid(Long id, Long payerId) {
        InternAllowanceRecord record = allowanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu phụ cấp ID: " + id));

        if (record.getStatus() == AllowanceStatus.PAID) {
            return mapToResponse(record);
        }

        record.setStatus(AllowanceStatus.PAID);
        record.setPaymentDate(LocalDate.now());
        InternAllowanceRecord saved = allowanceRepository.save(record);

        // Phat su kien dang tin cay sau commit (AFTER_COMMIT)
        String eventId = String.format("EVT-ALLOWANCE-%d-PAID-%d", saved.getId(), System.currentTimeMillis());
        AllowancePaidEvent event = AllowancePaidEvent.builder()
                .eventId(eventId)
                .recordId(saved.getId())
                .internUserId(saved.getInternUserId())
                .internFullName(saved.getInternProfile() != null ? saved.getInternProfile().getFullName() : "")
                .internEmail(saved.getInternProfile() != null ? saved.getInternProfile().getEmail() : "")
                .periodMonth(saved.getPeriodMonth())
                .actualAmount(saved.getActualAmount())
                .paymentDate(saved.getPaymentDate())
                .status(saved.getStatus())
                .build();
        eventPublisher.publishEvent(event);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public AllowanceRecordResponse createRetroAdjustment(RetroAdjustmentRequest request, Long hrUserId) {
        List<InternAllowanceRecord> targets = allowanceRepository
                .findByInternUserIdAndPeriodMonth(request.getInternUserId(), request.getTargetPeriodMonth());

        if (targets.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy phiếu phụ cấp kỳ đích "
                    + request.getTargetPeriodMonth() + " cho TTS ID: " + request.getInternUserId());
        }

        InternAllowanceRecord target = targets.get(0);
        if (target.isLocked()) {
            throw new BadRequestException("Kỳ mục tiêu đã bị khóa, không thể gán khoản bù vào kỳ này");
        }

        target.setRetroAdjustmentAmount(request.getAmount());
        target.setRetroRefPeriodMonth(request.getRefPeriodMonth());
        target.setRetroReason(request.getReason());

        // Tinh lai actualAmount
        BigDecimal base = target.getBaseAllowance();
        BigDecimal dailyRate = base.divide(BigDecimal.valueOf(target.getStandardWorkingDays()), 4, RoundingMode.HALF_UP);
        BigDecimal workingAmount = dailyRate.multiply(BigDecimal.valueOf(target.getActualWorkingDays() + target.getPaidLeaveDays()));
        BigDecimal newTotal = workingAmount
                .add(target.getBonusAmount())
                .subtract(target.getDeductionAmount())
                .add(request.getAmount());

        target.setActualAmount(roundToThousand(newTotal.max(BigDecimal.ZERO)));
        return mapToResponse(allowanceRepository.save(target));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllowanceRecordResponse> getMyHistory(Long internUserId) {
        return allowanceRepository.findByInternUserIdOrderByPeriodMonthDesc(internUserId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllowanceRecordResponse> getAllowancesByPeriod(String periodMonth, AllowanceStatus status) {
        return allowanceRepository.findByPeriodMonthAndOptionalStatus(periodMonth, status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllowanceRecordResponse> getInternHistory(Long internUserId) {
        return allowanceRepository.findByInternUserIdOrderByPeriodMonthDesc(internUserId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllowanceAuditLogResponse> getAuditLogs(Long recordId) {
        return auditLogRepository.findByAllowanceRecordIdOrderByAdjustedAtDesc(recordId).stream()
                .map(log -> AllowanceAuditLogResponse.builder()
                        .id(log.getId())
                        .allowanceRecordId(recordId)
                        .previousWorkingDays(log.getPreviousWorkingDays())
                        .previousPaidLeaveDays(log.getPreviousPaidLeaveDays())
                        .previousBonusAmount(log.getPreviousBonusAmount())
                        .previousDeductionAmount(log.getPreviousDeductionAmount())
                        .previousAmount(log.getPreviousAmount())
                        .newWorkingDays(log.getNewWorkingDays())
                        .newPaidLeaveDays(log.getNewPaidLeaveDays())
                        .newBonusAmount(log.getNewBonusAmount())
                        .newDeductionAmount(log.getNewDeductionAmount())
                        .newAmount(log.getNewAmount())
                        .adjustmentReason(log.getAdjustmentReason())
                        .adjustedById(log.getAdjustedById())
                        .adjustedByName(log.getAdjustedByName())
                        .adjustedAt(log.getAdjustedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private CalculationResult computeDaysAndAmount(InternProfile intern, Contract contract, YearMonth ym, int standardWorkingDays) {
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        LocalDate effectiveStart = contract.getEffectiveFrom() != null && contract.getEffectiveFrom().isAfter(monthStart)
                ? contract.getEffectiveFrom() : monthStart;
        LocalDate effectiveEnd = contract.getEffectiveTo() != null && contract.getEffectiveTo().isBefore(monthEnd)
                ? contract.getEffectiveTo() : monthEnd;

        int actualDays = 0;
        if (!effectiveStart.isAfter(effectiveEnd)) {
            List<Attendance> attendances = attendanceRepository
                    .findByInternIdAndWorkDateBetweenOrderByWorkDateAsc(intern.getId(), effectiveStart, effectiveEnd);

            actualDays = (int) attendances.stream()
                    .filter(a -> a.getCheckInTime() != null && a.getStatus() != AttendanceStatus.ABSENT)
                    .count();
        }

        // Tinh ngay nghi phep hop le trong khoang hieu luc hop dong
        int paidLeaveDays = 0; // Se lay them tu LeaveRequestRepository neu co don approved trong khoang nay

        BigDecimal base = contract.getAllowanceAmount() != null ? contract.getAllowanceAmount() : BigDecimal.ZERO;
        BigDecimal dailyRate = standardWorkingDays > 0
                ? base.divide(BigDecimal.valueOf(standardWorkingDays), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal rawAmount = dailyRate.multiply(BigDecimal.valueOf(actualDays + paidLeaveDays));
        BigDecimal roundedAmount = roundToThousand(rawAmount);

        return new CalculationResult(actualDays, paidLeaveDays, roundedAmount);
    }

    private BigDecimal roundToThousand(BigDecimal amount) {
        if (amount == null) return BigDecimal.ZERO;
        // Lam tron den 1000: chia 1000 lam tron roi nhan lai 1000
        BigDecimal divided = amount.divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP);
        return divided.multiply(BigDecimal.valueOf(1000));
    }

    private AllowanceRecordResponse mapToResponse(InternAllowanceRecord r) {
        return AllowanceRecordResponse.builder()
                .id(r.getId())
                .internUserId(r.getInternUserId())
                .internProfileId(r.getInternProfile() != null ? r.getInternProfile().getId() : null)
                .internFullName(r.getInternProfile() != null ? r.getInternProfile().getFullName() : null)
                .internEmail(r.getInternProfile() != null ? r.getInternProfile().getEmail() : null)
                .contractId(r.getContract() != null ? r.getContract().getId() : null)
                .contractNumber(r.getContract() != null ? r.getContract().getContractNumber() : null)
                .periodMonth(r.getPeriodMonth())
                .calculationType(r.getCalculationType())
                .baseAllowance(r.getBaseAllowance())
                .standardWorkingDays(r.getStandardWorkingDays())
                .autoCalculatedDays(r.getAutoCalculatedDays())
                .autoCalculatedAmount(r.getAutoCalculatedAmount())
                .actualWorkingDays(r.getActualWorkingDays())
                .paidLeaveDays(r.getPaidLeaveDays())
                .bonusAmount(r.getBonusAmount())
                .deductionAmount(r.getDeductionAmount())
                .retroAdjustmentAmount(r.getRetroAdjustmentAmount())
                .retroRefPeriodMonth(r.getRetroRefPeriodMonth())
                .retroReason(r.getRetroReason())
                .actualAmount(r.getActualAmount())
                .isManuallyAdjusted(r.getIsManuallyAdjusted())
                .status(r.getStatus())
                .isLocked(r.isLocked())
                .paymentDate(r.getPaymentDate())
                .notes(r.getNotes())
                .createdBy(r.getCreatedBy())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private static class CalculationResult {
        final int actualDays;
        final int paidLeaveDays;
        final BigDecimal calculatedAmount;

        CalculationResult(int actualDays, int paidLeaveDays, BigDecimal calculatedAmount) {
            this.actualDays = actualDays;
            this.paidLeaveDays = paidLeaveDays;
            this.calculatedAmount = calculatedAmount;
        }
    }
}
