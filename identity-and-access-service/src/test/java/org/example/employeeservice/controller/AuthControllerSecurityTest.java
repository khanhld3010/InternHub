package org.example.employeeservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.example.employeeservice.config.JwtProperties;
import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.dto.response.TokenRotationResult;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.exception.GlobalExceptionHandler;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.rbac.service.RoleManagementService;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.security.JwtTokenProvider;
import org.example.employeeservice.service.AuthService;
import org.example.employeeservice.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerSecurityTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private RoleManagementService roleManagementService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("TC-FE-02 & TC-SEC-02: Đăng nhập rememberMe=true -> Trả về HttpOnly Cookie với Max-Age 7 ngày (604800s)")
    void givenRememberMeTrue_whenLogin_thenSetPersistentCookie() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("Password123@");
        request.setRememberMe(true);

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("mock-access-token")
                .username("testuser")
                .build();

        Account mockAccount = Account.builder()
                .username("testuser")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(mockAccount));
        when(refreshTokenService.createRefreshToken(mockAccount, true)).thenReturn("raw-refresh-token-123");
        when(jwtProperties.getRefreshExpiration()).thenReturn(604800000L);
        when(jwtProperties.isCookieSecure()).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("internhub_refresh_token=raw-refresh-token-123")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=604800")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    @DisplayName("TC-FE-01: Đăng nhập rememberMe=false -> Trả về Session Cookie (không có Max-Age)")
    void givenRememberMeFalse_whenLogin_thenSetSessionCookie() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("Password123@");
        request.setRememberMe(false);

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("mock-access-token")
                .username("testuser")
                .build();

        Account mockAccount = Account.builder()
                .username("testuser")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(mockAccount));
        when(refreshTokenService.createRefreshToken(mockAccount, false)).thenReturn("raw-refresh-token-456");
        when(jwtProperties.isCookieSecure()).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("internhub_refresh_token=raw-refresh-token-456")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.not(containsString("Max-Age"))));
    }

    @Test
    @DisplayName("TC-ROT-01: Làm mới token với Cookie hợp lệ -> Xoay vòng và trả về Access Token mới cùng Cookie mới")
    void givenValidCookie_whenRefreshToken_thenRotateAndReturnNewToken() throws Exception {
        String oldCookieToken = "old-raw-token-123";
        String newRawToken = "new-raw-token-789";
        Account mockAccount = Account.builder().username("testuser").build();

        TokenRotationResult rotationResult = TokenRotationResult.builder()
                .account(mockAccount)
                .newRawRefreshToken(newRawToken)
                .rememberMe(true)
                .build();

        when(refreshTokenService.verifyAndRotate(eq(oldCookieToken), eq(true))).thenReturn(rotationResult);
        when(jwtTokenProvider.generateToken(mockAccount)).thenReturn("new-access-token-999");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(900L);
        when(jwtProperties.getRefreshExpiration()).thenReturn(604800000L);
        when(jwtProperties.isCookieSecure()).thenReturn(false);

        Cookie cookie = new Cookie("internhub_refresh_token", oldCookieToken);

        mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token-999"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("internhub_refresh_token=new-raw-token-789")));
    }

    @Test
    @DisplayName("TC-SEC-03: Kẻ gian Replay Token cũ đã revoked -> Nhận lỗi 401 Unauthorized")
    void givenRevokedToken_whenRefreshToken_thenThrow401Unauthorized() throws Exception {
        String compromisedToken = "compromised-token-111";

        when(refreshTokenService.verifyAndRotate(eq(compromisedToken), eq(true)))
                .thenThrow(new UnauthorizedException("Phát hiện Token Replay Attack! Toàn bộ phiên đã bị thu hồi."));

        Cookie cookie = new Cookie("internhub_refresh_token", compromisedToken);

        mockMvc.perform(post("/api/auth/refresh-token")
                        .cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message", containsString("Phát hiện Token Replay Attack")));
    }

    @Test
    @DisplayName("TC-AUTH-01: Đăng xuất tài khoản -> Thu hồi token và gửi Set-Cookie Max-Age=0")
    void givenActiveSession_whenLogout_thenRevokeTokenAndClearCookie() throws Exception {
        String activeToken = "active-token-to-revoke";
        Cookie cookie = new Cookie("internhub_refresh_token", activeToken);

        when(jwtProperties.isCookieSecure()).thenReturn(false);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("internhub_refresh_token=")));

        verify(refreshTokenService, times(1)).revokeToken(activeToken);
    }
}
