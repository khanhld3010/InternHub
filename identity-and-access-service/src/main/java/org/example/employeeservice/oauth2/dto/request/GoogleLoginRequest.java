package org.example.employeeservice.oauth2.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request payload cho API đăng nhập bằng Google OAuth2.
 * Tiếp nhận Google ID Token dạng JWT chuỗi từ Frontend Single Page App.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID Token không được để trống")
    private String idToken;
}
