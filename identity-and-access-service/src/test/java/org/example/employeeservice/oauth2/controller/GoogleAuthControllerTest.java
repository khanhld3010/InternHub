package org.example.employeeservice.oauth2.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.employeeservice.config.JwtProperties;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.exception.GlobalExceptionHandler;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.oauth2.dto.request.GoogleLoginRequest;
import org.example.employeeservice.oauth2.service.GoogleOAuth2Service;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GoogleOAuth2Service googleOAuth2Service;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private GoogleAuthController googleAuthController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(googleAuthController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("API Test: POST /api/auth/oauth2/google với idToken hợp lệ -> HTTP 200 OK")
    void givenValidRequest_whenLoginWithGoogle_thenReturn200() throws Exception {
        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("valid-google-id-token")
                .build();

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("mock-jwt-token")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .username("cuongle")
                .fullName("Lê Văn Cường")
                .email("cuong.le@gmail.com")
                .role("Intern")
                .userId(10)
                .build();

        when(googleOAuth2Service.loginWithGoogle(any(GoogleLoginRequest.class))).thenReturn(loginResponse);
        
        org.example.employeeservice.entity.Account mockAccount = org.example.employeeservice.entity.Account.builder()
                .id(1)
                .username("cuongle")
                .build();
        when(accountRepository.findByUsername("cuongle")).thenReturn(java.util.Optional.of(mockAccount));
        when(refreshTokenService.createRefreshToken(mockAccount, true)).thenReturn("raw-google-refresh-token-999");
        when(jwtProperties.getRefreshExpiration()).thenReturn(604800000L);
        when(jwtProperties.isCookieSecure()).thenReturn(false);

        mockMvc.perform(post("/api/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string(org.springframework.http.HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("internhub_refresh_token=raw-google-refresh-token-999")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng nhập bằng tài khoản Google thành công"))
                .andExpect(jsonPath("$.data.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.data.username").value("cuongle"))
                .andExpect(jsonPath("$.data.role").value("Intern"));
    }

    @Test
    @DisplayName("API Test: POST /api/auth/oauth2/google với idToken để trống -> HTTP 400 Bad Request")
    void givenBlankIdToken_whenLoginWithGoogle_thenReturn400() throws Exception {
        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("")
                .build();

        mockMvc.perform(post("/api/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API Test: POST /api/auth/oauth2/google khi token sai/hết hạn -> HTTP 401 Unauthorized")
    void givenInvalidToken_whenLoginWithGoogle_thenReturn401() throws Exception {
        GoogleLoginRequest request = GoogleLoginRequest.builder()
                .idToken("invalid-expired-token")
                .build();

        when(googleOAuth2Service.loginWithGoogle(any(GoogleLoginRequest.class)))
                .thenThrow(new UnauthorizedException("Google ID Token không hợp lệ hoặc đã hết hạn"));

        mockMvc.perform(post("/api/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Google ID Token không hợp lệ hoặc đã hết hạn"));
    }
}
