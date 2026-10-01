package org.example.employeeservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.config.JwtProperties;
import org.example.employeeservice.dto.response.TokenRotationResult;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.RefreshToken;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.repository.RefreshTokenRepository;
import org.example.employeeservice.service.RefreshTokenService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String createRefreshToken(Account account, boolean rememberMe) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        long refreshDurationMs = jwtProperties.getRefreshExpiration();
        LocalDateTime expiryDate = LocalDateTime.now().plusNanos(refreshDurationMs * 1_000_000);

        RefreshToken refreshToken = RefreshToken.builder()
                .account(account)
                .tokenHash(tokenHash)
                .expiryDate(expiryDate)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Khởi tạo Refresh Token thành công cho tài khoản: {}, rememberMe: {}", account.getUsername(), rememberMe);
        return rawToken;
    }

    @Override
    @Transactional
    public TokenRotationResult verifyAndRotate(String rawRefreshToken, boolean rememberMe) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh Token không được để trống");
        }

        String tokenHash = hashToken(rawRefreshToken.trim());
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Không tìm thấy Refresh Token với mã hash tương ứng");
                    return new UnauthorizedException("Refresh Token không hợp lệ hoặc đã hết hạn");
                });

        Account account = oldToken.getAccount();

        // 1. Phát hiện Tấn công Phát lại (Replay / Reuse Attack Detection)
        if (oldToken.isRevoked()) {
            log.error("CẢNH BÁO AN NINH: Phát hiện Token Replay Attack cho Account: {} (ID: {})!",
                    account.getUsername(), account.getId());
            revokeAllAccountTokens(account.getId());
            throw new UnauthorizedException("Cảnh báo an ninh: Phát hiện phiên làm việc bất thường. Toàn bộ phiên của tài khoản đã bị vô hiệu hóa vì lý do an toàn. Vui lòng đăng nhập lại.");
        }

        // 2. Kiểm tra thời hạn hết hạn
        if (oldToken.isExpired()) {
            log.warn("Refresh Token của tài khoản {} đã hết hạn vào {}", account.getUsername(), oldToken.getExpiryDate());
            oldToken.setRevoked(true);
            oldToken.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(oldToken);
            throw new UnauthorizedException("Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.");
        }

        // 3. Xoay vòng Token (Rotation): Thu hồi token cũ và sinh token mới
        String newRawToken = generateRawToken();
        String newTokenHash = hashToken(newRawToken);

        oldToken.setRevoked(true);
        oldToken.setRevokedAt(LocalDateTime.now());
        oldToken.setReplacedByTokenHash(newTokenHash);
        refreshTokenRepository.save(oldToken);

        long refreshDurationMs = jwtProperties.getRefreshExpiration();
        LocalDateTime newExpiryDate = LocalDateTime.now().plusNanos(refreshDurationMs * 1_000_000);

        RefreshToken newToken = RefreshToken.builder()
                .account(account)
                .tokenHash(newTokenHash)
                .expiryDate(newExpiryDate)
                .revoked(false)
                .build();

        refreshTokenRepository.save(newToken);
        log.info("Xoay vòng Refresh Token thành công cho tài khoản: {}", account.getUsername());

        return TokenRotationResult.builder()
                .account(account)
                .newRawRefreshToken(newRawToken)
                .rememberMe(rememberMe)
                .build();
    }

    @Override
    @Transactional
    public void revokeToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawRefreshToken.trim());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevoked(true);
                token.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(token);
                log.info("Đã thu hồi thành công Refresh Token cho tài khoản: {}", token.getAccount().getUsername());
            }
        });
    }

    @Override
    @Transactional
    public void revokeAllAccountTokens(Integer accountId) {
        if (accountId == null) {
            return;
        }
        refreshTokenRepository.revokeAllByAccountId(accountId, LocalDateTime.now());
        log.info("Đã thu hồi toàn bộ Refresh Tokens của Account ID: {}", accountId);
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 2 * * ?") // Chạy hàng ngày vào lúc 2:00 sáng
    public void cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(14);
        refreshTokenRepository.deleteExpiredTokens(cutoff);
        log.info("Đã hoàn tất dọn dẹp các Refresh Tokens hết hạn trước {}", cutoff);
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán mã hóa SHA-256 không khả dụng trên hệ thống", e);
        }
    }
}
