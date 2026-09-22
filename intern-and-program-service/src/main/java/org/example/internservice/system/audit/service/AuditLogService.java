package org.example.internservice.system.audit.service;

import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.system.audit.dto.request.AuditLogFilterRequest;
import org.example.internservice.system.audit.dto.response.AuditLogDetailResponse;
import org.example.internservice.system.audit.dto.response.AuditLogResponse;
import org.example.internservice.system.audit.dto.response.AuditLogStatsResponse;
import org.example.internservice.system.audit.entity.AuditLog;
import org.example.internservice.system.audit.event.AuditLogEvent;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {

    PageResponse<AuditLogResponse> getAuditLogs(AuditLogFilterRequest filterRequest, Pageable pageable);

    AuditLogDetailResponse getAuditLogDetail(Long id);

    AuditLogStatsResponse getAuditStatistics();

    byte[] exportAuditLogsCsv(AuditLogFilterRequest filterRequest);

    AuditLog saveAuditLog(AuditLogEvent event);
}
