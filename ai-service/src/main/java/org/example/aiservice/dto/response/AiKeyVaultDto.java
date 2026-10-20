package org.example.aiservice.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiKeyVaultDto {

    private Long id;
    private String keyName;
    private String provider;
    private String maskedKey;
    private Integer priority;
    private String status;
    private Long cooldownSecondsRemaining;
    private Integer failureCount;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
