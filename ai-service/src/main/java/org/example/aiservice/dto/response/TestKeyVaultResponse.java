package org.example.aiservice.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestKeyVaultResponse {

    private Long id;
    private String provider;
    private String status;
    private Long latencyMs;
    private String message;
    private String testedModel;
    private String quotaTier;
    private LocalDateTime testedAt;
}
