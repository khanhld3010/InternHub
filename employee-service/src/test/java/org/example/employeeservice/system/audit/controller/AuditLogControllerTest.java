package org.example.employeeservice.system.audit.controller;

import org.example.employeeservice.common.dto.response.PageResponse;
import org.example.employeeservice.system.audit.dto.request.AuditLogFilterRequest;
import org.example.employeeservice.system.audit.dto.response.AuditLogDetailResponse;
import org.example.employeeservice.system.audit.dto.response.AuditLogResponse;
import org.example.employeeservice.system.audit.dto.response.AuditLogStatsResponse;
import org.example.employeeservice.system.audit.entity.AuditAction;
import org.example.employeeservice.system.audit.entity.AuditModule;
import org.example.employeeservice.system.audit.entity.AuditStatus;
import org.example.employeeservice.system.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuditLogController auditLogController;

    private AuditLogResponse mockResponse;
    private AuditLogDetailResponse mockDetailResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(auditLogController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        mockResponse = AuditLogResponse.builder()
                .id(101L)
                .userId(1L)
                .username("admin@internhub.vn")
                .userRole("ADMIN")
                .action(AuditAction.TRIGGER_BACKUP)
                .module(AuditModule.SYSTEM)
                .description("Kích hoạt sao lưu hệ thống")
                .endpoint("/api/system/backups")
                .httpMethod("POST")
                .clientIp("192.168.1.50")
                .status(AuditStatus.SUCCESS)
                .executionTimeMs(250L)
                .createdAt(LocalDateTime.now())
                .build();

        mockDetailResponse = AuditLogDetailResponse.builder()
                .id(101L)
                .userId(1L)
                .username("admin@internhub.vn")
                .userRole("ADMIN")
                .action(AuditAction.TRIGGER_BACKUP)
                .module(AuditModule.SYSTEM)
                .description("Kích hoạt sao lưu hệ thống")
                .endpoint("/api/system/backups")
                .httpMethod("POST")
                .clientIp("192.168.1.50")
                .userAgent("Chrome/128.0")
                .status(AuditStatus.SUCCESS)
                .executionTimeMs(250L)
                .requestPayload("{\"type\":\"MANUAL\"}")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("IT-BE-01: GET /api/system/audit-logs should return 200 and paged data")
    void getAuditLogs_shouldReturn200AndPagedList() throws Exception {
        PageResponse<AuditLogResponse> pageResponse = PageResponse.<AuditLogResponse>builder()
                .items(List.of(mockResponse))
                .currentPage(0)
                .pageSize(20)
                .totalItems(1)
                .totalPages(1)
                .isFirst(true)
                .isLast(true)
                .build();

        when(auditLogService.getAuditLogs(any(AuditLogFilterRequest.class), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/system/audit-logs")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].username").value("admin@internhub.vn"))
                .andExpect(jsonPath("$.data.items[0].action").value("TRIGGER_BACKUP"));
    }

    @Test
    @DisplayName("IT-BE-02: GET /api/system/audit-logs/{id} should return 200 and detail")
    void getAuditLogDetail_shouldReturn200AndDetail() throws Exception {
        when(auditLogService.getAuditLogDetail(101L)).thenReturn(mockDetailResponse);

        mockMvc.perform(get("/api/system/audit-logs/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.userAgent").value("Chrome/128.0"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("IT-BE-03: GET /api/system/audit-logs/statistics should return 200 and stats")
    void getAuditStatistics_shouldReturn200AndStats() throws Exception {
        AuditLogStatsResponse statsResponse = AuditLogStatsResponse.builder()
                .totalToday(50)
                .totalSuccess(48)
                .totalFailed(2)
                .successRate(96.0)
                .moduleBreakdown(new HashMap<>())
                .build();

        when(auditLogService.getAuditStatistics()).thenReturn(statsResponse);

        mockMvc.perform(get("/api/system/audit-logs/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalToday").value(50))
                .andExpect(jsonPath("$.data.successRate").value(96.0));
    }

    @Test
    @DisplayName("IT-BE-04: GET /api/system/audit-logs/export should return 200 and CSV stream")
    void exportAuditLogsCsv_shouldReturn200AndCsvFile() throws Exception {
        byte[] mockCsv = "\uFEFFID,Thời Gian,Người Thực Hiện\n101,2026-09-21,admin\n".getBytes(StandardCharsets.UTF_8);

        when(auditLogService.exportAuditLogsCsv(any(AuditLogFilterRequest.class))).thenReturn(mockCsv);

        mockMvc.perform(get("/api/system/audit-logs/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment; filename=\"audit_logs_")));
    }
}
