package org.example.internservice.system.audit.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.internservice.common.dto.response.PageResponse;
import org.example.internservice.exception.ResourceNotFoundException;
import org.example.internservice.system.audit.dto.request.AuditLogFilterRequest;
import org.example.internservice.system.audit.dto.response.AuditLogDetailResponse;
import org.example.internservice.system.audit.dto.response.AuditLogResponse;
import org.example.internservice.system.audit.dto.response.AuditLogStatsResponse;
import org.example.internservice.system.audit.entity.AuditLog;
import org.example.internservice.system.audit.entity.AuditModule;
import org.example.internservice.system.audit.entity.AuditStatus;
import org.example.internservice.system.audit.event.AuditLogEvent;
import org.example.internservice.system.audit.repository.AuditLogRepository;
import org.example.internservice.system.audit.repository.AuditLogSpecification;
import org.example.internservice.system.audit.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private static final DateTimeFormatter CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageResponse<AuditLogResponse> getAuditLogs(AuditLogFilterRequest filterRequest, Pageable pageable) {
        // Enforce max page size to prevent memory exhaustion
        int pageSize = Math.min(pageable.getPageSize(), 100);
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageSize, pageable.getSort());

        Specification<AuditLog> spec = AuditLogSpecification.build(filterRequest);
        Page<AuditLog> page = auditLogRepository.findAll(spec, safePageable);

        return PageResponse.from(page, this::toResponseDto);
    }

    @Override
    public AuditLogDetailResponse getAuditLogDetail(Long id) {
        AuditLog logItem = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi nhật ký hoạt động với ID: " + id));

        return AuditLogDetailResponse.builder()
                .id(logItem.getId())
                .userId(logItem.getUserId())
                .username(logItem.getUsername())
                .userRole(logItem.getUserRole())
                .action(logItem.getAction())
                .module(logItem.getModule())
                .description(logItem.getDescription())
                .endpoint(logItem.getEndpoint())
                .httpMethod(logItem.getHttpMethod())
                .clientIp(logItem.getClientIp())
                .userAgent(logItem.getUserAgent())
                .status(logItem.getStatus())
                .executionTimeMs(logItem.getExecutionTimeMs())
                .errorMessage(logItem.getErrorMessage())
                .requestPayload(logItem.getRequestPayload())
                .createdAt(logItem.getCreatedAt())
                .build();
    }

    @Override
    public AuditLogStatsResponse getAuditStatistics() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        long totalToday = auditLogRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long totalSuccess = auditLogRepository.countByStatusAndCreatedAtBetween(AuditStatus.SUCCESS, startOfDay, endOfDay);
        long totalFailed = auditLogRepository.countByStatusAndCreatedAtBetween(AuditStatus.FAILED, startOfDay, endOfDay);

        double successRate = totalToday > 0
                ? Math.round(((double) totalSuccess / totalToday * 100.0) * 100.0) / 100.0
                : 100.0;

        Map<String, Long> moduleBreakdown = new HashMap<>();
        for (AuditModule module : AuditModule.values()) {
            moduleBreakdown.put(module.name(), 0L);
        }

        List<Object[]> moduleCounts = auditLogRepository.countByModuleBetween(startOfDay, endOfDay);
        for (Object[] row : moduleCounts) {
            if (row.length >= 2 && row[0] != null && row[1] != null) {
                AuditModule mod = (AuditModule) row[0];
                Long count = (Long) row[1];
                moduleBreakdown.put(mod.name(), count);
            }
        }

        return AuditLogStatsResponse.builder()
                .totalToday(totalToday)
                .totalSuccess(totalSuccess)
                .totalFailed(totalFailed)
                .successRate(successRate)
                .moduleBreakdown(moduleBreakdown)
                .build();
    }

    @Override
    public byte[] exportAuditLogsCsv(AuditLogFilterRequest filterRequest) {
        Specification<AuditLog> spec = AuditLogSpecification.build(filterRequest);
        // Limit export to top 5000 most recent records
        Pageable exportPageable = PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<AuditLog> records = auditLogRepository.findAll(spec, exportPageable).getContent();

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Microsoft Excel compatibility
        sb.append("\uFEFF");
        sb.append("ID,Thời Gian,Người Thực Hiện,Vai Trò,Phân Hệ,Hành Động,Mô Tả,Endpoint,Phương Thức,Địa Chỉ IP,Trạng Thái,Thời Gian Xử Lý (ms)\n");

        for (AuditLog record : records) {
            sb.append(record.getId()).append(",");
            sb.append(record.getCreatedAt() != null ? record.getCreatedAt().format(CSV_DATE_FORMAT) : "").append(",");
            sb.append(escapeCsv(record.getUsername())).append(",");
            sb.append(escapeCsv(record.getUserRole())).append(",");
            sb.append(record.getModule()).append(",");
            sb.append(record.getAction()).append(",");
            sb.append(escapeCsv(record.getDescription())).append(",");
            sb.append(escapeCsv(record.getEndpoint())).append(",");
            sb.append(record.getHttpMethod()).append(",");
            sb.append(escapeCsv(record.getClientIp())).append(",");
            sb.append(record.getStatus()).append(",");
            sb.append(record.getExecutionTimeMs() != null ? record.getExecutionTimeMs() : 0);
            sb.append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public AuditLog saveAuditLog(AuditLogEvent event) {
        AuditLog auditLog = AuditLog.builder()
                .userId(event.getUserId())
                .username(event.getUsername() != null ? event.getUsername() : "SYSTEM")
                .userRole(event.getUserRole())
                .action(event.getAction())
                .module(event.getModule())
                .description(event.getDescription() != null ? event.getDescription() : "")
                .endpoint(event.getEndpoint() != null ? event.getEndpoint() : "")
                .httpMethod(event.getHttpMethod() != null ? event.getHttpMethod() : "UNKNOWN")
                .clientIp(event.getClientIp())
                .userAgent(event.getUserAgent())
                .status(event.getStatus())
                .executionTimeMs(event.getExecutionTimeMs())
                .errorMessage(event.getErrorMessage())
                .requestPayload(event.getRequestPayload())
                .build();

        return auditLogRepository.save(auditLog);
    }

    private AuditLogResponse toResponseDto(AuditLog entity) {
        return AuditLogResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .userRole(entity.getUserRole())
                .action(entity.getAction())
                .module(entity.getModule())
                .description(entity.getDescription())
                .endpoint(entity.getEndpoint())
                .httpMethod(entity.getHttpMethod())
                .clientIp(entity.getClientIp())
                .status(entity.getStatus())
                .executionTimeMs(entity.getExecutionTimeMs())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
