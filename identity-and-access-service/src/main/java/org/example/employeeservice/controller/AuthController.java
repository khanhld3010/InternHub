package org.example.employeeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.config.JwtProperties;
import org.example.employeeservice.dto.request.ActivateAccountRequest;
import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.request.RefreshTokenRequest;
import org.example.employeeservice.dto.request.RegisterRequest;
import org.example.employeeservice.dto.request.ResendActivationRequest;
import org.example.employeeservice.dto.response.ApiResponse;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.dto.response.RefreshTokenResponse;
import org.example.employeeservice.dto.response.RegisterResponse;
import org.example.employeeservice.dto.response.TokenRotationResult;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.rbac.dto.response.UserPermissionsResponse;
import org.example.employeeservice.rbac.service.RoleManagementService;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.security.JwtTokenProvider;
import org.example.employeeservice.service.AuthService;
import org.example.employeeservice.service.RefreshTokenService;
import org.example.employeeservice.system.audit.annotation.Auditable;
import org.example.employeeservice.system.audit.entity.AuditAction;
import org.example.employeeservice.system.audit.entity.AuditModule;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/employees/auth", "/api/auth"})
@RequiredArgsConstructor
@Tag(name = "Authentication Controller", description = "Quản lý đăng nhập, đăng ký tài khoản, kích hoạt email, làm mới phiên ngầm và thu hồi token")
public class AuthController {

    private final AuthService authService;
    private final RoleManagementService roleManagementService;
    private final RefreshTokenService refreshTokenService;
    private final AccountRepository accountRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Operation(summary = "Đăng nhập hệ thống bằng username/email và password (TM-32)")
    @Auditable(action = AuditAction.LOGIN_SUCCESS, module = AuditModule.AUTH, description = "Người dùng đăng nhập vào hệ thống")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Nhận yêu cầu đăng nhập từ user: {}", request.getUsername());
        LoginResponse response = authService.login(request);

        Account account = accountRepository.findByUsername(response.getUsername()).orElse(null);
        if (account != null) {
            boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());
            String rawRefreshToken = refreshTokenService.createRefreshToken(account, rememberMe);
            ResponseCookie refreshCookie = createRefreshCookie(rawRefreshToken, rememberMe);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(ApiResponse.success("Đăng nhập thành công", response));
        }

        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @Operation(summary = "Làm mới Access Token ngầm và xoay vòng Refresh Token (TM-32)")
    @Auditable(action = AuditAction.LOGIN_SUCCESS, module = AuditModule.AUTH, description = "Làm mới Access Token ngầm qua Refresh Token")
    @PostMapping({"/refresh-token", "/refresh"})
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @CookieValue(name = "internhub_refresh_token", required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest requestBody) {

        String rawToken = (cookieToken != null && !cookieToken.isBlank())
                ? cookieToken
                : (requestBody != null ? requestBody.getRefreshToken() : null);

        if (rawToken == null || rawToken.isBlank()) {
            log.warn("Yêu cầu refresh token không chứa cookie hoặc body token");
            throw new UnauthorizedException("Không tìm thấy Refresh Token hợp lệ");
        }

        boolean rememberMe = (requestBody != null && Boolean.TRUE.equals(requestBody.getRememberMe()))
                || (cookieToken != null);

        TokenRotationResult rotationResult = refreshTokenService.verifyAndRotate(rawToken, rememberMe);
        Account account = rotationResult.getAccount();
        String newRawToken = rotationResult.getNewRawRefreshToken();

        String newAccessToken = jwtTokenProvider.generateToken(account);
        ResponseCookie newCookie = createRefreshCookie(newRawToken, rotationResult.isRememberMe());

        RefreshTokenResponse responseData = RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .build();

        log.info("Cấp mới Access Token thành công cho tài khoản: {}", account.getUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(ApiResponse.success("Làm mới token thành công", responseData));
    }

    @Operation(summary = "Đăng xuất tài khoản, thu hồi Refresh Token và xóa Cookie (TM-32)")
    @Auditable(action = AuditAction.LOGOUT, module = AuditModule.AUTH, description = "Đăng xuất tài khoản người dùng")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "internhub_refresh_token", required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest requestBody) {

        String rawToken = (cookieToken != null && !cookieToken.isBlank())
                ? cookieToken
                : (requestBody != null ? requestBody.getRefreshToken() : null);

        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokenService.revokeToken(rawToken);
        }

        ResponseCookie cleanCookie = createCleanCookie();
        log.info("Đăng xuất thành công, đã gửi chỉ thị xóa Cookie");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(ApiResponse.success("Đăng xuất thành công", null));
    }

    @Operation(summary = "Đăng ký tài khoản người dùng mới (Public Endpoint - Nhận 1 Object tổng hợp)")
    @Auditable(action = AuditAction.REGISTER, module = AuditModule.AUTH, description = "Đăng ký tài khoản người dùng mới")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Nhận yêu cầu đăng ký tài khoản từ username: {}, email: {}", request.getUsername(), request.getEmail());
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công! Vui lòng kiểm tra email để nhận mã kích hoạt.", response));
    }

    @Operation(summary = "Xác thực mã OTP kích hoạt tài khoản (TM-10 v1.4)")
    @Auditable(action = AuditAction.ACTIVATE_SUCCESS, module = AuditModule.AUTH, description = "Kích hoạt tài khoản người dùng qua mã OTP")
    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<Void>> activateAccount(@Valid @RequestBody ActivateAccountRequest request) {
        log.info("Nhận yêu cầu kích hoạt tài khoản cho: {}", request.getIdentifier());
        authService.activateAccount(request);
        return ResponseEntity.ok(ApiResponse.success("Kích hoạt tài khoản thành công! Bạn có thể đăng nhập ngay bây giờ.", null));
    }

    @Operation(summary = "Gửi lại mã OTP kích hoạt tài khoản (TM-10 v1.4)")
    @Auditable(action = AuditAction.RESEND_ACTIVATION, module = AuditModule.AUTH, description = "Yêu cầu gửi lại mã kích hoạt tài khoản")
    @PostMapping("/resend-activation")
    public ResponseEntity<ApiResponse<Void>> resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        log.info("Nhận yêu cầu gửi lại mã kích hoạt cho: {}", request.getIdentifier());
        authService.resendActivation(request);
        return ResponseEntity.ok(ApiResponse.success("Mã kích hoạt mới đã được gửi vào hòm thư email của bạn.", null));
    }

    @Operation(summary = "Lấy thông tin tài khoản đang đăng nhập")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Chưa xác thực"));
        }
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", authentication.getName());
        userInfo.put("authorities", authentication.getAuthorities());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tài khoản thành công", userInfo));
    }

    @Operation(summary = "Lấy chi tiết danh sách đặc quyền (permissions) của tài khoản đang đăng nhập")
    @GetMapping("/me/permissions")
    public ResponseEntity<ApiResponse<UserPermissionsResponse>> getCurrentUserPermissions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa xác thực"));
        }
        UserPermissionsResponse response = roleManagementService.getUserPermissions(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin quyền hạn người dùng thành công", response));
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

    private ResponseCookie createCleanCookie() {
        return ResponseCookie.from("internhub_refresh_token", "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(0)
                .build();
    }
}
