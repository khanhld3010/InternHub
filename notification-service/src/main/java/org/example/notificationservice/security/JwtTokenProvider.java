package org.example.notificationservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${jwt.secret-key}") String secretKeyString) {
        this.secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
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
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
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
