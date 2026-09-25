package org.example.employeeservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.config.JwtProperties;
import org.example.employeeservice.entity.Account;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            String secret = jwtProperties.getSecretKey();
            if (secret.matches("^[0-9a-fA-F]+$") && secret.length() >= 64) {
                keyBytes = java.util.HexFormat.of().parseHex(secret);
            } else {
                keyBytes = Decoders.BASE64.decode(secret);
            }
        } catch (Exception e) {
            keyBytes = jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Tạo JWT token từ thông tin Account
     */
    public String generateToken(Account account) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getExpiration());

        String roleName = (account.getRole() != null) ? account.getRole().getName().toUpperCase() : "USER";

        return Jwts.builder()
                .subject(account.getUsername())
                .claim("userId", account.getUserId())
                .claim("role", roleName)
                .claim("accountId", account.getId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Trích xuất username từ JWT token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /**
     * Trích xuất Claims từ JWT token
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Kiểm tra tính hợp lệ của JWT token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("JWT token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public long getExpirationInSeconds() {
        return jwtProperties.getExpiration() / 1000;
    }
}
