package org.example.reportingservice.system.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.reportingservice.common.entity.BaseEntity;

@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_audit_module", columnList = "module"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_status", columnList = "status"),
        @Index(name = "idx_audit_username", columnList = "username"),
        @Index(name = "idx_audit_composite", columnList = "created_at DESC, module, status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 50)
    private AuditModule module;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "endpoint", nullable = false, length = 255)
    private String endpoint;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuditStatus status;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_payload", columnDefinition = "JSON")
    private String requestPayload;
}
