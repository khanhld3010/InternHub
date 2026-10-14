package org.example.notificationservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${jwt.secret-key}") String secretKeyString) {
        this.secretKey = getSigningKey(secretKeyString);
    }

    private SecretKey getSigningKey(String secret) {
        byte[] keyBytes;
        try {
            if (secret != null && secret.matches("^[0-9a-fA-F]+$") && secret.length() >= 64) {
                keyBytes = java.util.HexFormat.of().parseHex(secret);
            } else if (secret != null) {
                keyBytes = Decoders.BASE64.decode(secret);
            } else {
                keyBytes = new byte[0];
            }
        } catch (Exception e) {
            log.warn("Lỗi giải mã JWT secret (HEX/Base64), fallback sang UTF-8 bytes: {}", e.getMessage());
            keyBytes = secret != null ? secret.getBytes(StandardCharsets.UTF_8) : new byte[0];
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            boolean isValid = claims.getExpiration().after(new Date());
            if (!isValid) {
                log.warn("JWT token đã hết hạn cho subject: {}", claims.getSubject());
            }
            return isValid;
        } catch (Exception e) {
            log.warn("Xác thực JWT token thất bại: {} ({})", e.getMessage(), e.getClass().getSimpleName());
            return false;
        }
    }

    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj != null) {
            return Long.valueOf(userIdObj.toString());
        }
        // Fallback to subject
        return Long.valueOf(claims.getSubject());
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        Object role = claims.get("role");
        if (role == null) {
            role = claims.get("roles");
        }
        return role != null ? role.toString() : "ROLE_USER";
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }
}
