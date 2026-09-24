package org.example.internservice.intern.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@Slf4j
public class OnboardingTokenService {

    private final SecretKey key;
    private final long expirationMs = 7L * 24 * 60 * 60 * 1000; // 7 ngày

    public OnboardingTokenService(@Value("${jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateOnboardingToken(Long internId, String email, String fullName) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(internId))
                .claim("email", email)
                .claim("fullName", fullName)
                .claim("type", "ONBOARDING_ACTIVATION")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public Claims parseAndVerifyToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("Token onboarding khong hop le hoac da het han: {}", e.getMessage());
            throw new IllegalArgumentException("Liên kết kích hoạt không hợp lệ hoặc đã hết hạn", e);
        }
    }
}
