package org.example.employeeservice.system.audit.service;

import org.example.employeeservice.common.dto.response.PageResponse;
import org.example.employeeservice.system.audit.dto.request.AuditLogFilterRequest;
import org.example.employeeservice.system.audit.dto.response.AuditLogDetailResponse;
import org.example.employeeservice.system.audit.dto.response.AuditLogResponse;
import org.example.employeeservice.system.audit.dto.response.AuditLogStatsResponse;
import org.example.employeeservice.system.audit.entity.AuditLog;
import org.example.employeeservice.system.audit.event.AuditLogEvent;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {

    PageResponse<AuditLogResponse> getAuditLogs(AuditLogFilterRequest filterRequest, Pageable pageable);

    AuditLogDetailResponse getAuditLogDetail(Long id);

    AuditLogStatsResponse getAuditStatistics();

    byte[] exportAuditLogsCsv(AuditLogFilterRequest filterRequest);

    AuditLog saveAuditLog(AuditLogEvent event);
}
