package org.example.employeeservice.system.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.employeeservice.system.entity.BackupStatus;
import org.example.employeeservice.system.entity.BackupType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupFilterRequest {
    private BackupStatus status;
    private BackupType backupType;
}
