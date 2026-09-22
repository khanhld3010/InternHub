package org.example.reportingservice.system.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.reportingservice.system.entity.BackupStatus;
import org.example.reportingservice.system.entity.BackupType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupFilterRequest {
    private BackupStatus status;
    private BackupType backupType;
}
