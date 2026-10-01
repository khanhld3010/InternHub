package org.example.employeeservice.oauth2.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình thông tin tích hợp Google OAuth2.
 * Tuân thủ Rule #12: Không đọc trực tiếp file .env, nạp thông qua Spring Properties với giá trị fallback an toàn.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.oauth2.google")
public class GoogleOAuth2Properties {

    /**
     * Google Client ID đăng ký trên Google Cloud Console.
     * Mặc định fallback là "mock-google-client-id" phục vụ môi trường kiểm thử và phát triển nội bộ.
     */
    private String clientId = "mock-google-client-id";
}
