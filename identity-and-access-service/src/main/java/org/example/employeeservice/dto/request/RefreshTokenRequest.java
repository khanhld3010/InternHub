package org.example.employeeservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenRequest {

    /**
     * Refresh Token thô (truyền qua request body nếu client không hỗ trợ Cookie)
     */
    private String refreshToken;

    @Builder.Default
    private Boolean rememberMe = false;
}
