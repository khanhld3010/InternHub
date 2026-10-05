package org.example.notificationservice.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSessionMeta> activeSessions = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, WebSocketSessionMeta meta) {
        activeSessions.put(sessionId, meta);
        log.info("WebSocket session registered: sessionId={}, userId={}", sessionId, meta.getUserId());
    }

    public void removeSession(String sessionId) {
        WebSocketSessionMeta removed = activeSessions.remove(sessionId);
        if (removed != null) {
            log.info("WebSocket session removed: sessionId={}, userId={}", sessionId, removed.getUserId());
        }
    }

    public Map<String, WebSocketSessionMeta> getAllSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    public List<WebSocketSessionMeta> getSessionsByUserId(Long userId) {
        List<WebSocketSessionMeta> list = new ArrayList<>();
        for (WebSocketSessionMeta meta : activeSessions.values()) {
            if (meta.getUserId() != null && meta.getUserId().equals(userId)) {
                list.add(meta);
            }
        }
        return list;
    }

    public List<WebSocketSessionMeta> getSessionsByRole(String role) {
        if (role == null) {
            return Collections.emptyList();
        }
        String cleanRole = role.replace("ROLE_", "");
        List<WebSocketSessionMeta> list = new ArrayList<>();
        for (WebSocketSessionMeta meta : activeSessions.values()) {
            if (meta.getRole() != null) {
                String metaRole = meta.getRole().replace("ROLE_", "");
                if (metaRole.equalsIgnoreCase(cleanRole)) {
                    list.add(meta);
                }
            }
        }
        return list;
    }

    public Set<Long> getDistinctUserIdsByRole(String role) {
        if (role == null) {
            return Collections.emptySet();
        }
        String cleanRole = role.replace("ROLE_", "");
        Set<Long> userIds = new HashSet<>();
        for (WebSocketSessionMeta meta : activeSessions.values()) {
            if (meta.getRole() != null && meta.getUserId() != null) {
                String metaRole = meta.getRole().replace("ROLE_", "");
                if (metaRole.equalsIgnoreCase(cleanRole)) {
                    userIds.add(meta.getUserId());
                }
            }
        }
        return userIds;
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        removeSession(sessionId);
    }
}
