package org.example.employeeservice.system.audit.aspect;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.employeeservice.dto.request.ActivateAccountRequest;
import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.request.RegisterRequest;
import org.example.employeeservice.dto.request.ResendActivationRequest;
import org.example.employeeservice.oauth2.dto.request.GoogleLoginRequest;
import org.example.employeeservice.system.audit.annotation.Auditable;
import org.example.employeeservice.system.audit.entity.AuditAction;
import org.example.employeeservice.system.audit.entity.AuditStatus;
import org.example.employeeservice.system.audit.event.AuditLogEvent;
import org.example.employeeservice.system.audit.util.DataMaskingUtils;
import org.example.employeeservice.system.audit.util.IpUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final ApplicationEventPublisher eventPublisher;

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long startTime = System.currentTimeMillis();
        AuditStatus status = AuditStatus.SUCCESS;
        String errorMessage = null;
        Object result;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            status = AuditStatus.FAILED;
            errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            throw ex;
        } finally {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            try {
                captureAndPublishAuditLog(joinPoint, auditable, status, executionTimeMs, errorMessage);
            } catch (Exception e) {
                log.error("Lỗi khi phát sinh sự kiện ghi nhật ký hoạt động: {}", e.getMessage(), e);
            }
        }
    }

    private void captureAndPublishAuditLog(
            ProceedingJoinPoint joinPoint,
            Auditable auditable,
            AuditStatus status,
            long executionTimeMs,
            String errorMessage
    ) {
        HttpServletRequest request = getCurrentHttpRequest();

        String endpoint = (request != null) ? request.getRequestURI() : "INTERNAL";
        String httpMethod = (request != null) ? request.getMethod() : "INTERNAL";
        String clientIp = (request != null) ? IpUtils.getClientIp(request) : "127.0.0.1";
        String userAgent = (request != null) ? request.getHeader("User-Agent") : "System-Internal";

        String username = "SYSTEM";
        String userRole = "SYSTEM";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            username = auth.getName();
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            if (authorities != null && !authorities.isEmpty()) {
                userRole = authorities.iterator().next().getAuthority().replace("ROLE_", "");
            } else {
                userRole = "AUTHENTICATED";
            }
        } else {
            // Trường hợp login payload có thể chứa username
            username = extractUsernameFromArgs(joinPoint.getArgs(), username);
            userRole = "ANONYMOUS";
        }

        AuditAction action = auditable.action();
        if (status == AuditStatus.FAILED) {
            if (action == AuditAction.LOGIN_SUCCESS) {
                action = AuditAction.LOGIN_FAILED;
            } else if (action == AuditAction.ACTIVATE_SUCCESS) {
                action = AuditAction.ACTIVATE_FAILED;
            }
        }

        String description = auditable.description();
        if (description == null || description.trim().isEmpty()) {
            description = String.format("Thực thi %s trong phân hệ %s", action, auditable.module());
        }

        String payload = extractAndMaskPayload(joinPoint.getArgs());

        AuditLogEvent event = AuditLogEvent.builder()
                .username(username)
                .userRole(userRole)
                .action(action)
                .module(auditable.module())
                .description(description)
                .endpoint(endpoint)
                .httpMethod(httpMethod)
                .clientIp(clientIp)
                .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                .status(status)
                .executionTimeMs(executionTimeMs)
                .errorMessage(errorMessage)
                .requestPayload(payload)
                .build();

        eventPublisher.publishEvent(event);
    }

    private HttpServletRequest getCurrentHttpRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return (attributes != null) ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUsernameFromArgs(Object[] args, String defaultUsername) {
        if (args == null) return defaultUsername;
        for (Object arg : args) {
            if (arg instanceof LoginRequest loginReq) {
                if (loginReq.getUsername() != null && !loginReq.getUsername().isBlank()) {
                    return loginReq.getUsername().trim();
                }
            } else if (arg instanceof RegisterRequest regReq) {
                if (regReq.getUsername() != null && !regReq.getUsername().isBlank()) {
                    return regReq.getUsername().trim();
                }
            } else if (arg instanceof ActivateAccountRequest actReq) {
                if (actReq.getIdentifier() != null && !actReq.getIdentifier().isBlank()) {
                    return actReq.getIdentifier().trim();
                }
            } else if (arg instanceof ResendActivationRequest resendReq) {
                if (resendReq.getIdentifier() != null && !resendReq.getIdentifier().isBlank()) {
                    return resendReq.getIdentifier().trim();
                }
            } else if (arg instanceof GoogleLoginRequest) {
                return "GOOGLE_USER";
            } else if (arg instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) arg;
                if (map.containsKey("username")) {
                    return String.valueOf(map.get("username"));
                }
                if (map.containsKey("identifier")) {
                    return String.valueOf(map.get("identifier"));
                }
            }
        }
        return defaultUsername;
    }

    private String extractAndMaskPayload(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        try {
            Map<String, Object> payloadMap = new HashMap<>();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null
                        || arg instanceof HttpServletRequest
                        || arg instanceof HttpServletResponse
                        || arg instanceof MultipartFile
                        || arg instanceof Authentication
                        || arg instanceof BindingResult) {
                    continue;
                }
                payloadMap.put("arg_" + i, arg);
            }

            if (payloadMap.isEmpty()) {
                return null;
            }

            // Nếu chỉ có đúng 1 DTO/object
            if (payloadMap.size() == 1) {
                Object singleObj = payloadMap.values().iterator().next();
                return DataMaskingUtils.maskObject(singleObj);
            }

            return DataMaskingUtils.maskObject(payloadMap);
        } catch (Exception e) {
            log.debug("Không thể trích xuất payload: {}", e.getMessage());
            return null;
        }
    }
}
