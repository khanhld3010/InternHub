package org.example.internservice.attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInQrResponse {

    private String qrToken;
    private String qrCodeDataUrl;
    private String confirmationUrl;
    private Integer expiresInSeconds;
    private LocalDateTime expiresAt;
    private Double distance;
    private String officeName;
    private String message;
}
