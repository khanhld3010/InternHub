package org.example.employeeservice.service;

import org.example.employeeservice.config.JwtProperties;
import org.example.employeeservice.dto.response.TokenRotationResult;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.RefreshToken;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.repository.RefreshTokenRepository;
import org.example.employeeservice.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .id(100)
                .username("test_intern")
                .status("ACTIVE")
                .build();

        lenient().when(jwtProperties.getRefreshExpiration()).thenReturn(604800000L); // 7 ngày
    }

    @Test
    @DisplayName("UT-BE-01: Tạo mới Refresh Token thành công, sinh rawToken 64 hex và lưu hash vào DB")
    void givenValidAccount_whenCreateRefreshToken_thenReturnValidRawTokenAndSaveHashInDb() {
        // When
        String rawToken = refreshTokenService.createRefreshToken(testAccount, true);

        // Then
        assertNotNull(rawToken);
        assertEquals(64, rawToken.length());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("UT-BE-02: Xoay vòng Refresh Token hợp lệ - Đánh dấu thu hồi token cũ và sinh token mới")
    void givenValidRawToken_whenVerifyAndRotate_thenRevokeOldTokenAndIssueNewPair() {
        // Given
        RefreshToken existingToken = RefreshToken.builder()
                .account(testAccount)
                .tokenHash("some_token_hash")
                .expiryDate(LocalDateTime.now().plusDays(5))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));

        // When
        TokenRotationResult result = refreshTokenService.verifyAndRotate("valid_raw_token_value", true);

        // Then
        assertNotNull(result);
        assertEquals(testAccount, result.getAccount());
        assertNotNull(result.getNewRawRefreshToken());
        assertEquals(64, result.getNewRawRefreshToken().length());
        assertTrue(existingToken.isRevoked());
        assertNotNull(existingToken.getRevokedAt());
        assertNotNull(existingToken.getReplacedByTokenHash());

        // Đã lưu bản ghi cập nhật token cũ và lưu token mới
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("UT-BE-03: Xoay vòng Refresh Token đã hết hạn - Ném lỗi UnauthorizedException")
    void givenExpiredToken_whenVerifyAndRotate_thenThrowUnauthorizedException() {
        // Given
        RefreshToken expiredToken = RefreshToken.builder()
                .account(testAccount)
                .tokenHash("expired_token_hash")
                .expiryDate(LocalDateTime.now().minusDays(1)) // Đã hết hạn
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        // When & Then
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                refreshTokenService.verifyAndRotate("expired_raw_token_value", false));

        assertTrue(exception.getMessage().contains("Phiên làm việc đã hết hạn"));
        assertTrue(expiredToken.isRevoked());
        verify(refreshTokenRepository, times(1)).save(expiredToken);
    }

    @Test
    @DisplayName("UT-BE-04: Phát hiện Token Replay Attack khi token đã revoked - Thu hồi toàn bộ token tài khoản")
    void givenRevokedToken_whenVerifyAndRotate_thenDetectReplayAndRevokeAllAccountTokens() {
        // Given
        RefreshToken revokedToken = RefreshToken.builder()
                .account(testAccount)
                .tokenHash("revoked_token_hash")
                .expiryDate(LocalDateTime.now().plusDays(3))
                .revoked(true) // Token đã bị thu hồi trước đó
                .revokedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        // When & Then
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () ->
                refreshTokenService.verifyAndRotate("stolen_revoked_token", false));

        assertTrue(exception.getMessage().contains("Cảnh báo an ninh: Phát hiện phiên làm việc bất thường"));

        // Cascade revoke toàn bộ token của tài khoản đó
        verify(refreshTokenRepository, times(1)).revokeAllByAccountId(eq(testAccount.getId()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("UT-BE-05: Thu hồi Refresh Token khi đăng xuất - Cập nhật revoked = true")
    void givenValidToken_whenRevokeToken_thenMarkRevokedSuccessfully() {
        // Given
        RefreshToken activeToken = RefreshToken.builder()
                .account(testAccount)
                .tokenHash("active_token_hash")
                .expiryDate(LocalDateTime.now().plusDays(2))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(activeToken));

        // When
        refreshTokenService.revokeToken("raw_token_to_logout");

        // Then
        assertTrue(activeToken.isRevoked());
        assertNotNull(activeToken.getRevokedAt());
        verify(refreshTokenRepository, times(1)).save(activeToken);
    }
}
