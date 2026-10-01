package org.example.employeeservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Khóa bí mật ký JWT (ít nhất 256 bits cho HS256)
     */
    private String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    /**
     * Thời gian hết hạn của Access Token tính bằng millisecond (mặc định 15 phút = 900000ms)
     */
    private long expiration = 900000L;

    /**
     * Thời gian hết hạn của Refresh Token tính bằng millisecond (mặc định 7 ngày = 604800000ms)
     */
    private long refreshExpiration = 604800000L;

    /**
     * Cờ Secure cho Cookie (mặc định false cho môi trường dev localhost, true trên production HTTPS)
     */
    private boolean cookieSecure = false;
}
