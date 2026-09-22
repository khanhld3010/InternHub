package org.example.reportingservice.system.audit.service;

import org.example.reportingservice.common.dto.response.PageResponse;
import org.example.reportingservice.exception.ResourceNotFoundException;
import org.example.reportingservice.system.audit.dto.request.AuditLogFilterRequest;
import org.example.reportingservice.system.audit.dto.response.AuditLogDetailResponse;
import org.example.reportingservice.system.audit.dto.response.AuditLogResponse;
import org.example.reportingservice.system.audit.dto.response.AuditLogStatsResponse;
import org.example.reportingservice.system.audit.entity.AuditAction;
import org.example.reportingservice.system.audit.entity.AuditLog;
import org.example.reportingservice.system.audit.entity.AuditModule;
import org.example.reportingservice.system.audit.entity.AuditStatus;
import org.example.reportingservice.system.audit.repository.AuditLogRepository;
import org.example.reportingservice.system.audit.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    private AuditLog mockAuditLog;

    @BeforeEach
    void setUp() {
        mockAuditLog = AuditLog.builder()
                .userId(1L)
                .username("admin@internhub.vn")
                .userRole("ADMIN")
                .action(AuditAction.TRIGGER_BACKUP)
                .module(AuditModule.SYSTEM)
                .description("Kích hoạt sao lưu hệ thống")
                .endpoint("/api/system/backups")
                .httpMethod("POST")
                .clientIp("192.168.1.50")
                .userAgent("Mozilla/5.0")
                .status(AuditStatus.SUCCESS)
                .executionTimeMs(250L)
                .requestPayload("{\"type\":\"MANUAL\"}")
                .build();
        mockAuditLog.setId(101L);
    }

    @Test
    @DisplayName("UT-BE-04: getAuditLogs should return paged responses")
    void getAuditLogs_shouldReturnPagedData() {
        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> mockPage = new PageImpl<>(List.of(mockAuditLog), pageable, 1);

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        PageResponse<AuditLogResponse> response = auditLogService.getAuditLogs(filter, pageable);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(AuditAction.TRIGGER_BACKUP, response.getItems().get(0).getAction());
        assertEquals("admin@internhub.vn", response.getItems().get(0).getUsername());
        verify(auditLogRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("UT-BE-05: getAuditLogDetail should return detail when ID exists")
    void getAuditLogDetail_whenIdExists_shouldReturnDetail() {
        when(auditLogRepository.findById(101L)).thenReturn(Optional.of(mockAuditLog));

        AuditLogDetailResponse response = auditLogService.getAuditLogDetail(101L);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals(AuditStatus.SUCCESS, response.getStatus());
        assertEquals("{\"type\":\"MANUAL\"}", response.getRequestPayload());
        assertEquals("Mozilla/5.0", response.getUserAgent());
    }

    @Test
    @DisplayName("UT-BE-06: getAuditLogDetail should throw ResourceNotFoundException when ID does not exist")
    void getAuditLogDetail_whenIdNotFound_shouldThrowException() {
        when(auditLogRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> auditLogService.getAuditLogDetail(999L));
    }

    @Test
    @DisplayName("UT-BE-07: getAuditStatistics should return correct calculated statistics")
    void getAuditStatistics_shouldReturnCalculatedStats() {
        when(auditLogRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(100L);
        when(auditLogRepository.countByStatusAndCreatedAtBetween(eq(AuditStatus.SUCCESS), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(95L);
        when(auditLogRepository.countByStatusAndCreatedAtBetween(eq(AuditStatus.FAILED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(auditLogRepository.countByModuleBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        AuditLogStatsResponse stats = auditLogService.getAuditStatistics();

        assertNotNull(stats);
        assertEquals(100L, stats.getTotalToday());
        assertEquals(95L, stats.getTotalSuccess());
        assertEquals(5L, stats.getTotalFailed());
        assertEquals(95.0, stats.getSuccessRate());
    }

    @Test
    @DisplayName("UT-BE-08: exportAuditLogsCsv should generate valid CSV bytes with BOM")
    void exportAuditLogsCsv_shouldReturnValidCsvBytes() {
        AuditLogFilterRequest filter = new AuditLogFilterRequest();
        Page<AuditLog> mockPage = new PageImpl<>(List.of(mockAuditLog));

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        byte[] csvBytes = auditLogService.exportAuditLogsCsv(filter);

        assertNotNull(csvBytes);
        assertTrue(csvBytes.length > 0);
        String csvContent = new String(csvBytes);
        assertTrue(csvContent.contains("ID,Thời Gian,Người Thực Hiện"));
        assertTrue(csvContent.contains("admin@internhub.vn"));
        assertTrue(csvContent.contains("TRIGGER_BACKUP"));
    }
}
