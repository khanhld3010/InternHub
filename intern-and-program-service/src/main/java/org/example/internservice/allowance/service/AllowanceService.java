package org.example.internservice.allowance.service;

import org.example.internservice.allowance.dto.request.AdjustAllowanceRequest;
import org.example.internservice.allowance.dto.request.BatchApproveAllowanceRequest;
import org.example.internservice.allowance.dto.request.RetroAdjustmentRequest;
import org.example.internservice.allowance.dto.request.UnlockPeriodRequest;
import org.example.internservice.allowance.dto.response.AllowanceAuditLogResponse;
import org.example.internservice.allowance.dto.response.AllowanceRecordResponse;
import org.example.internservice.allowance.dto.response.BatchApproveResultResponse;
import org.example.internservice.allowance.entity.enums.AllowanceStatus;

import java.util.List;

public interface AllowanceService {

    List<AllowanceRecordResponse> calculatePreview(String periodMonth);

    List<AllowanceRecordResponse> batchSave(String periodMonth, Long hrUserId);

    AllowanceRecordResponse adjustAllowance(Long id, AdjustAllowanceRequest request, Long hrUserId, String hrUserName);

    BatchApproveResultResponse batchApprove(BatchApproveAllowanceRequest request, Long approverId);

    void unlockPeriod(String periodMonth, UnlockPeriodRequest request, Long unlockerId);

    AllowanceRecordResponse markPaid(Long id, Long payerId);

    AllowanceRecordResponse createRetroAdjustment(RetroAdjustmentRequest request, Long hrUserId);

    List<AllowanceRecordResponse> getMyHistory(Long internUserId);

    List<AllowanceRecordResponse> getAllowancesByPeriod(String periodMonth, AllowanceStatus status);

    List<AllowanceRecordResponse> getInternHistory(Long internUserId);

    List<AllowanceAuditLogResponse> getAuditLogs(Long recordId);
}
