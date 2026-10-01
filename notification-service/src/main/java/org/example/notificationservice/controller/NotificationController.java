package org.example.notificationservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.notificationservice.dto.CreateNotificationRequest;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.exception.ForbiddenException;
import org.example.notificationservice.security.UserPrincipal;
import org.example.notificationservice.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Value("${internal.service-token:internhub-internal-secret-token-2026}")
    private String internalServiceToken;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(notificationService.getNotifications(principal.getUserId(), cursorCreatedAt, cursorId, limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        long count = notificationService.getUnreadCount(principal.getUserId());
        Map<String, Object> res = new HashMap<>();
        res.put("unreadCount", count);
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(notificationService.markAsRead(id, principal.getUserId()));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        int updated = notificationService.markAllAsRead(principal.getUserId());
        Map<String, Object> res = new HashMap<>();
        res.put("updatedCount", updated);
        return ResponseEntity.ok(res);
    }

    // Endpoint nội bộ cho các service khác gọi tạo thông báo
    @PostMapping("/internal")
    public ResponseEntity<NotificationResponse> createInternal(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody CreateNotificationRequest request) {
        if (token == null || !token.equals(internalServiceToken)) {
            throw new ForbiddenException("Invalid or missing internal service token");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createAndDispatch(request));
    }

    // Endpoint nội bộ cho các service khác gọi phát lệnh bảo mật khẩn cấp (FORCE_LOGOUT, ACCOUNT_LOCKED)
    @PostMapping("/internal/security-command")
    public ResponseEntity<Map<String, Object>> dispatchSecurityCommand(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody org.example.notificationservice.dto.SecurityCommandMessage command) {
        if (token == null || !token.equals(internalServiceToken)) {
            throw new ForbiddenException("Invalid or missing internal service token");
        }
        notificationService.dispatchSecurityCommand(command);
        Map<String, Object> res = new HashMap<>();
        res.put("status", "DISPATCHED");
        res.put("action", command.getAction());
        res.put("userId", command.getUserId());
        return ResponseEntity.ok(res);
    }
}
