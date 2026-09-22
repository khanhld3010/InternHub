package org.example.internservice.system.audit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.internservice.system.audit.entity.AuditAction;
import org.example.internservice.system.audit.entity.AuditModule;
import org.example.internservice.system.audit.entity.AuditStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private String username;
    private String userRole;
    private AuditAction action;
    private AuditModule module;
    private String description;
    private String endpoint;
    private String httpMethod;
    private String clientIp;
    private AuditStatus status;
    private Long executionTimeMs;
    private LocalDateTime createdAt;
}
