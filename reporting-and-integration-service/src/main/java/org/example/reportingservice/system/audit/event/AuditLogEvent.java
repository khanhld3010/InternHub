package org.example.reportingservice.system.audit.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.reportingservice.system.audit.entity.AuditAction;
import org.example.reportingservice.system.audit.entity.AuditModule;
import org.example.reportingservice.system.audit.entity.AuditStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEvent {

    private Long userId;
    private String username;
    private String userRole;
    private AuditAction action;
    private AuditModule module;
    private String description;
    private String endpoint;
    private String httpMethod;
    private String clientIp;
    private String userAgent;
    private AuditStatus status;
    private Long executionTimeMs;
    private String errorMessage;
    private String requestPayload;
}
