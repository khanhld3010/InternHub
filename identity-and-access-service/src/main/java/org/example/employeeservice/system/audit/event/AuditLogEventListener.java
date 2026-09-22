package org.example.employeeservice.system.audit.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.system.audit.entity.AuditLog;
import org.example.employeeservice.system.audit.repository.AuditLogRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAuditLogEvent(AuditLogEvent event) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(event.getUserId())
                    .username(event.getUsername() != null ? event.getUsername() : "SYSTEM")
                    .userRole(event.getUserRole())
                    .action(event.getAction())
                    .module(event.getModule())
                    .description(event.getDescription() != null ? event.getDescription() : "")
                    .endpoint(event.getEndpoint() != null ? event.getEndpoint() : "")
                    .httpMethod(event.getHttpMethod() != null ? event.getHttpMethod() : "UNKNOWN")
                    .clientIp(event.getClientIp())
                    .userAgent(event.getUserAgent())
                    .status(event.getStatus())
                    .executionTimeMs(event.getExecutionTimeMs())
                    .errorMessage(event.getErrorMessage())
                    .requestPayload(event.getRequestPayload())
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Đã lưu audit log thành công: ID={}, action={}, status={}", auditLog.getId(), auditLog.getAction(), auditLog.getStatus());
        } catch (Exception e) {
            log.error("Lỗi khi ghi nhận audit log vào cơ sở dữ liệu: {}", e.getMessage(), e);
        }
    }
}
