package org.example.reportingservice.system.service;

import org.example.reportingservice.exception.ResourceNotFoundException;
import org.example.reportingservice.system.dto.response.BackupResponse;
import org.example.reportingservice.system.entity.BackupHistory;
import org.example.reportingservice.system.entity.BackupStatus;
import org.example.reportingservice.system.entity.BackupType;
import org.example.reportingservice.system.exception.BackupConflictException;
import org.example.reportingservice.system.repository.BackupHistoryRepository;
import org.example.reportingservice.system.service.impl.SystemBackupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemBackupServiceTest {

    @Mock
    private BackupHistoryRepository backupHistoryRepository;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Mock
    private ResultSet tablesResultSet;

    @InjectMocks
    private SystemBackupServiceImpl systemBackupService;

    @TempDir
    Path tempStorageDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(systemBackupService, "storageDir", tempStorageDir.toString());
        ReflectionTestUtils.setField(systemBackupService, "retentionDays", 30);
        ReflectionTestUtils.setField(systemBackupService, "mysqldumpPath", "invalid_mysqldump_command");
        ReflectionTestUtils.setField(systemBackupService, "dbUsername", "root");
        ReflectionTestUtils.setField(systemBackupService, "dbPassword", "123456");
        ReflectionTestUtils.setField(systemBackupService, "dbUrl", "jdbc:mysql://localhost:3307/internhub_db");
    }

    @Test
    @DisplayName("UT-BE-01: Kích hoạt sao lưu thành công khi không có tiến trình chạy song song")
    void triggerBackup_whenNoProcessRunning_shouldExecuteSuccessfully() throws Exception {
        when(backupHistoryRepository.existsByStatus(BackupStatus.IN_PROGRESS)).thenReturn(false);

        // Giả lập JDBC connection cho cơ chế fallback
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(connection.getCatalog()).thenReturn("internhub_db");
        when(databaseMetaData.getTables(any(), any(), any(), any())).thenReturn(tablesResultSet);
        when(tablesResultSet.next()).thenReturn(false);

        BackupHistory inProgressRecord = BackupHistory.builder()
                .fileName("internhub_backup_test.sql.gz")
                .filePath(tempStorageDir.resolve("internhub_backup_test.sql.gz").toString())
                .fileSize(0L)
                .backupType(BackupType.MANUAL)
                .status(BackupStatus.IN_PROGRESS)
                .createdBy("ADMIN")
                .build();
        inProgressRecord.setId(1L);

        when(backupHistoryRepository.saveAndFlush(any(BackupHistory.class))).thenReturn(inProgressRecord);
        when(backupHistoryRepository.save(any(BackupHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BackupResponse response = systemBackupService.triggerBackup(BackupType.MANUAL, "ADMIN");

        assertNotNull(response);
        assertEquals(BackupStatus.SUCCESS, response.getStatus());
        assertEquals(BackupType.MANUAL, response.getBackupType());
        verify(backupHistoryRepository, atLeastOnce()).save(any(BackupHistory.class));
    }

    @Test
    @DisplayName("UT-BE-02: Ném BackupConflictException khi DB đã có bản ghi IN_PROGRESS")
    void triggerBackup_whenAnotherProcessRunning_shouldThrowConflictException() {
        when(backupHistoryRepository.existsByStatus(BackupStatus.IN_PROGRESS)).thenReturn(true);

        assertThrows(BackupConflictException.class, () ->
                systemBackupService.triggerBackup(BackupType.MANUAL, "ADMIN")
        );

        verify(backupHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-BE-04: Tự động dọn dẹp các bản sao lưu cũ hơn 30 ngày")
    void cleanOldBackups_shouldDeleteFilesOlderThanRetentionDays() throws IOException {
        Path oldFile = tempStorageDir.resolve("old_backup.sql.gz");
        Files.createFile(oldFile);

        BackupHistory oldRecord = BackupHistory.builder()
                .fileName("old_backup.sql.gz")
                .filePath(oldFile.toString())
                .fileSize(100L)
                .status(BackupStatus.SUCCESS)
                .build();
        oldRecord.setId(99L);

        when(backupHistoryRepository.findByCreatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(oldRecord));

        systemBackupService.cleanOldBackups();

        assertFalse(Files.exists(oldFile), "File cũ phải bị xóa vật lý");
        verify(backupHistoryRepository).delete(oldRecord);
    }

    @Test
    @DisplayName("UT-BE-05: Tải bản sao lưu thành công khi file vật lý tồn tại trên đĩa")
    void downloadBackup_whenFileExists_shouldReturnValidResource() throws IOException {
        Path validFile = tempStorageDir.resolve("internhub_backup_valid.sql.gz");
        Files.writeString(validFile, "TEST_BACKUP_CONTENT");

        BackupHistory record = BackupHistory.builder()
                .fileName("internhub_backup_valid.sql.gz")
                .filePath(validFile.toString())
                .fileSize((long) "TEST_BACKUP_CONTENT".length())
                .status(BackupStatus.SUCCESS)
                .build();
        record.setId(10L);

        when(backupHistoryRepository.findById(10L)).thenReturn(Optional.of(record));

        Resource resource = systemBackupService.downloadBackupFile(10L);
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertEquals("internhub_backup_valid.sql.gz", resource.getFilename());
    }

    @Test
    @DisplayName("UT-BE-06: Ném ResourceNotFoundException khi ID bản sao lưu không tồn tại")
    void downloadBackup_whenRecordNotFound_shouldThrowResourceNotFoundException() {
        when(backupHistoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                systemBackupService.downloadBackupFile(999L)
        );
    }

    @Test
    @DisplayName("UT-BE-07: Xóa thành công bản sao lưu và file vật lý")
    void deleteBackup_shouldDeleteFileAndRecord() throws IOException {
        Path fileToDelete = tempStorageDir.resolve("to_delete.sql.gz");
        Files.createFile(fileToDelete);

        BackupHistory record = BackupHistory.builder()
                .fileName("to_delete.sql.gz")
                .filePath(fileToDelete.toString())
                .status(BackupStatus.SUCCESS)
                .build();
        record.setId(5L);

        when(backupHistoryRepository.findById(5L)).thenReturn(Optional.of(record));

        systemBackupService.deleteBackup(5L);

        assertFalse(Files.exists(fileToDelete));
        verify(backupHistoryRepository).delete(record);
    }
}
