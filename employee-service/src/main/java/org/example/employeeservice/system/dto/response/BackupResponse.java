package org.example.employeeservice.system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.employeeservice.system.entity.BackupHistory;
import org.example.employeeservice.system.entity.BackupStatus;
import org.example.employeeservice.system.entity.BackupType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupResponse {

    private Long id;
    private String fileName;
    private Long fileSize;
    private String formattedSize;
    private BackupType backupType;
    private BackupStatus status;
    private String scope;
    private Long durationMs;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime createdAt;

    public static BackupResponse fromEntity(BackupHistory entity) {
        return BackupResponse.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .formattedSize(formatFileSize(entity.getFileSize()))
                .backupType(entity.getBackupType())
                .status(entity.getStatus())
                .scope(entity.getScope())
                .durationMs(entity.getDurationMs())
                .errorMessage(entity.getErrorMessage())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static String formatFileSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return String.format(java.util.Locale.US, "%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
