package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityCommandMessage {
    private String action; // FORCE_LOGOUT, ACCOUNT_LOCKED, TOKEN_EXPIRED
    private Long userId;
    private String reason;
    private String message;
}
