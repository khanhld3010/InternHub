package org.example.notificationservice.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketSessionMeta {
    private String sessionId;
    private Long userId;
    private String role;
    private Date tokenExpirationTime;
    private long lastValidatedAt;
}
