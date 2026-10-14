package org.example.fileservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * JWT Token Provider cho file-service.
 * Chỉ xác thực (validate) token – KHÔNG tạo token mới.
 * Sử dụng cùng thuật toán giải mã hex giống identity-and-access-service.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;

    public JwtTokenProvider(@Value("${jwt.secret-key}") String secret) {
        byte[] keyBytes;
        try {
            if (secret.matches("^[0-9a-fA-F]+$") && secret.length() >= 64) {
                keyBytes = java.util.HexFormat.of().parseHex(secret);
            } else {
                keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(secret);
            }
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** Kiểm tra tính hợp lệ của JWT token */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /** Trích xuất username (subject) từ JWT token */
    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /** Trích xuất danh sách permission codes từ claim "permissions" */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        try {
            Object perms = getClaims(token).get("permissions");
            if (perms instanceof List<?>) {
                return (List<String>) perms;
            }
        } catch (Exception ex) {
            log.warn("Không thể trích xuất permissions từ token: {}", ex.getMessage());
        }
        return Collections.emptyList();
    }

    /** Trích xuất role từ claim "role" */
    public String getRoleFromToken(String token) {
        try {
            return (String) getClaims(token).get("role");
        } catch (Exception ex) {
            return null;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
