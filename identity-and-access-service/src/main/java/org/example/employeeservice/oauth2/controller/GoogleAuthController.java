package org.example.employeeservice.oauth2.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.dto.response.ApiResponse;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.config.JwtProperties;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.oauth2.dto.request.GoogleLoginRequest;
import org.example.employeeservice.oauth2.service.GoogleOAuth2Service;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.service.RefreshTokenService;
import org.example.employeeservice.system.audit.annotation.Auditable;
import org.example.employeeservice.system.audit.entity.AuditAction;
import org.example.employeeservice.system.audit.entity.AuditModule;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller tiếp nhận yêu cầu đăng nhập bằng Google OAuth2 (Google Sign-In).
 * Cung cấp public endpoints cho Single Page App Client.
 */
@Slf4j
@RestController
@RequestMapping({"/api/employees/auth", "/api/auth"})
@RequiredArgsConstructor
@Tag(name = "Google OAuth2 Controller", description = "Xử lý xác thực đăng nhập người dùng bằng tài khoản Google")
public class GoogleAuthController {

    private final GoogleOAuth2Service googleOAuth2Service;
    private final RefreshTokenService refreshTokenService;
    private final AccountRepository accountRepository;
    private final JwtProperties jwtProperties;

    @Operation(summary = "Đăng nhập hoặc đăng ký tài khoản tự động bằng Google ID Token (TM-30)")
    @Auditable(action = AuditAction.LOGIN_SUCCESS, module = AuditModule.AUTH, description = "Đăng nhập hệ thống qua Google OAuth2")
    @PostMapping("/oauth2/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        log.info("Nhận yêu cầu đăng nhập qua Google OAuth2");
        LoginResponse response = googleOAuth2Service.loginWithGoogle(request);

        Account account = accountRepository.findByUsername(response.getUsername()).orElse(null);
        if (account != null) {
            // Mặc định cho phiên Google OAuth2 lưu Refresh Token dài hạn (rememberMe = true)
            String rawRefreshToken = refreshTokenService.createRefreshToken(account, true);
            ResponseCookie refreshCookie = createRefreshCookie(rawRefreshToken, true);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(ApiResponse.success("Đăng nhập bằng tài khoản Google thành công", response));
        }

        return ResponseEntity.ok(ApiResponse.success("Đăng nhập bằng tài khoản Google thành công", response));
    }

    private ResponseCookie createRefreshCookie(String token, boolean rememberMe) {
        long maxAge = rememberMe ? (jwtProperties.getRefreshExpiration() / 1000) : -1;
        return ResponseCookie.from("internhub_refresh_token", token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }
}
