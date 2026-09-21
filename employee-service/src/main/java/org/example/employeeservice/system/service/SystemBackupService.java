package org.example.employeeservice.system.service;

import org.example.employeeservice.common.dto.response.PageResponse;
import org.example.employeeservice.system.dto.request.BackupFilterRequest;
import org.example.employeeservice.system.dto.response.BackupResponse;
import org.example.employeeservice.system.entity.BackupType;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;

public interface SystemBackupService {

    BackupResponse triggerBackup(BackupType backupType, String createdBy);

    PageResponse<BackupResponse> getBackupHistory(BackupFilterRequest request, Pageable pageable);

    Resource downloadBackupFile(Long id);

    String getBackupFileName(Long id);

    void deleteBackup(Long id);

    void cleanOldBackups();
}
