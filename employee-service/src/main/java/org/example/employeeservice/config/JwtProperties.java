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
     * Thời gian hết hạn của Access Token tính bằng millisecond (mặc định 24h = 86400000ms)
     */
    private long expiration = 86400000L;
}
