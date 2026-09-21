package org.example.employeeservice.system.controller;

import org.example.employeeservice.common.dto.response.PageResponse;
import org.example.employeeservice.system.dto.response.BackupResponse;
import org.example.employeeservice.system.entity.BackupStatus;
import org.example.employeeservice.system.entity.BackupType;
import org.example.employeeservice.system.service.SystemBackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SystemBackupControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SystemBackupService systemBackupService;

    @InjectMocks
    private SystemBackupController systemBackupController;

    private BackupResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(systemBackupController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        mockResponse = BackupResponse.builder()
                .id(1L)
                .fileName("internhub_backup_20260921_120000.sql.gz")
                .fileSize(1048576L)
                .formattedSize("1.00 MB")
                .backupType(BackupType.MANUAL)
                .status(BackupStatus.SUCCESS)
                .durationMs(1500L)
                .createdBy("ADMIN")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("IT-BE-01: POST /api/system/backups -> Trả về HTTP 201 Created")
    void triggerManualBackup_shouldReturnCreated() throws Exception {
        when(systemBackupService.triggerBackup(eq(BackupType.MANUAL), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/system/backups"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.fileName").value("internhub_backup_20260921_120000.sql.gz"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("IT-BE-03: GET /api/system/backups -> Trả về HTTP 200 OK với phân trang")
    void getBackupHistory_shouldReturnOk() throws Exception {
        PageResponse<BackupResponse> pageResponse = PageResponse.<BackupResponse>builder()
                .items(List.of(mockResponse))
                .currentPage(0)
                .pageSize(10)
                .totalItems(1L)
                .totalPages(1)
                .isFirst(true)
                .isLast(true)
                .build();

        when(systemBackupService.getBackupHistory(any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/system/backups")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    @DisplayName("IT-BE-04: GET /api/system/backups/{id}/download -> Trả về HTTP 200 OK với Content-Type gzip")
    void downloadBackup_shouldReturnOkAndFileStream() throws Exception {
        ByteArrayResource resource = new ByteArrayResource("MOCK_GZIP_BINARY".getBytes());
        when(systemBackupService.downloadBackupFile(1L)).thenReturn(resource);
        when(systemBackupService.getBackupFileName(1L)).thenReturn("internhub_backup_20260921_120000.sql.gz");

        mockMvc.perform(get("/api/system/backups/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/gzip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"internhub_backup_20260921_120000.sql.gz\""))
                .andExpect(content().string("MOCK_GZIP_BINARY"));
    }

    @Test
    @DisplayName("IT-BE-05: DELETE /api/system/backups/{id} -> Trả về HTTP 200 OK")
    void deleteBackup_shouldReturnOk() throws Exception {
        doNothing().when(systemBackupService).deleteBackup(1L);

        mockMvc.perform(delete("/api/system/backups/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Xóa bản sao lưu thành công"));
    }
}
