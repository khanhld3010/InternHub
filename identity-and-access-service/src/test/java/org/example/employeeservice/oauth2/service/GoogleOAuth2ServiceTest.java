package org.example.employeeservice.oauth2.service;

import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.entity.User;
import org.example.employeeservice.exception.BadRequestException;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.oauth2.dto.request.GoogleLoginRequest;
import org.example.employeeservice.oauth2.dto.response.GoogleUserInfo;
import org.example.employeeservice.oauth2.service.impl.GoogleOAuth2ServiceImpl;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.example.employeeservice.repository.UserRepository;
import org.example.employeeservice.security.JwtTokenProvider;
import org.example.employeeservice.system.audit.util.DataMaskingUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2ServiceTest {

    @Mock
    private GoogleTokenVerifierService tokenVerifierService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private GoogleOAuth2ServiceImpl googleOAuth2Service;

    private GoogleLoginRequest validRequest;
    private GoogleUserInfo validUserInfo;
    private Role internRole;

    @BeforeEach
    void setUp() {
        validRequest = GoogleLoginRequest.builder()
                .idToken("mock-valid-google-id-token")
                .build();

        validUserInfo = GoogleUserInfo.builder()
                .email("cuong.le@gmail.com")
                .name("Lê Văn Cường")
                .picture("https://google.com/avatar.jpg")
                .sub("google-sub-123456")
                .emailVerified(true)
                .build();

        internRole = Role.builder()
                .id(4)
                .name("Intern")
                .build();
    }

    @Test
    @DisplayName("UT-BE-01: Gửi Google ID Token hợp lệ khi chưa có User -> Khởi tạo User + Account Intern và trả về LoginResponse")
    void givenValidGoogleIdToken_whenUserNotExists_thenProvisionUserAndAccountAndReturnLoginResponse() {
        // Given
        when(tokenVerifierService.verify(validRequest.getIdToken())).thenReturn(validUserInfo);
        when(userRepository.findByEmail("cuong.le@gmail.com")).thenReturn(Optional.empty());

        User savedUser = User.builder()
                .id(101)
                .fullName("Lê Văn Cường")
                .email("cuong.le@gmail.com")
                .avatarUrl("https://google.com/avatar.jpg")
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(accountRepository.existsByUsername(anyString())).thenReturn(false);
        when(roleRepository.findByName("Intern")).thenReturn(Optional.of(internRole));
        when(passwordEncoder.encode(anyString())).thenReturn("mock-hashed-password");

        Account savedAccount = Account.builder()
                .id(202)
                .userId(101)
                .username("cuongle")
                .passwordHash("mock-hashed-password")
                .role(internRole)
                .status("ACTIVE")
                .authProvider("GOOGLE")
                .providerId("google-sub-123456")
                .build();
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        when(jwtTokenProvider.generateToken(any(Account.class))).thenReturn("mock-jwt-access-token");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(86400L);

        // When
        LoginResponse response = googleOAuth2Service.loginWithGoogle(validRequest);

        // Then
        assertNotNull(response);
        assertEquals("mock-jwt-access-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(86400L, response.getExpiresIn());
        assertEquals("cuongle", response.getUsername());
        assertEquals("cuong.le@gmail.com", response.getEmail());
        assertEquals("Intern", response.getRole());
        assertEquals(101, response.getUserId());

        verify(userRepository).save(any(User.class));
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("UT-BE-02: Gửi Google ID Token với User có trạng thái PENDING_ACTIVATION -> Kích hoạt ACTIVE và trả về LoginResponse")
    void givenValidGoogleIdToken_whenUserExistsPendingActivation_thenActivateAccountAndReturnLoginResponse() {
        // Given
        when(tokenVerifierService.verify(validRequest.getIdToken())).thenReturn(validUserInfo);

        User existingUser = User.builder()
                .id(50)
                .fullName("Lê Văn Cường")
                .email("cuong.le@gmail.com")
                .build();
        when(userRepository.findByEmail("cuong.le@gmail.com")).thenReturn(Optional.of(existingUser));

        Account pendingAccount = Account.builder()
                .id(80)
                .userId(50)
                .username("cuongle_intern")
                .status("PENDING_ACTIVATION")
                .role(internRole)
                .build();
        when(accountRepository.findByUserId(50)).thenReturn(Optional.of(pendingAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(jwtTokenProvider.generateToken(any(Account.class))).thenReturn("mock-jwt-token-activated");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(86400L);

        // When
        LoginResponse response = googleOAuth2Service.loginWithGoogle(validRequest);

        // Then
        assertNotNull(response);
        assertEquals("ACTIVE", pendingAccount.getStatus());
        assertEquals("GOOGLE", pendingAccount.getAuthProvider());
        assertEquals("google-sub-123456", pendingAccount.getProviderId());
        assertNotNull(pendingAccount.getLastLoginAt());
        verify(accountRepository).save(pendingAccount);
    }

    @Test
    @DisplayName("UT-BE-03: Gửi Google ID Token với User có trạng thái ACTIVE và vai trò Quản trị (HR) -> Giữ nguyên vai trò HR")
    void givenValidGoogleIdToken_whenUserExistsActive_thenUpdateLastLoginAndReturnLoginResponse() {
        // Given
        when(tokenVerifierService.verify(validRequest.getIdToken())).thenReturn(validUserInfo);

        User existingUser = User.builder()
                .id(2)
                .fullName("Trần Thị Bích")
                .email("cuong.le@gmail.com")
                .build();
        when(userRepository.findByEmail("cuong.le@gmail.com")).thenReturn(Optional.of(existingUser));

        Role hrRole = Role.builder().id(2).name("HR").build();
        Account activeHrAccount = Account.builder()
                .id(10)
                .userId(2)
                .username("hr_bich")
                .status("ACTIVE")
                .role(hrRole)
                .build();
        when(accountRepository.findByUserId(2)).thenReturn(Optional.of(activeHrAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(jwtTokenProvider.generateToken(any(Account.class))).thenReturn("mock-jwt-hr-token");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(86400L);

        // When
        LoginResponse response = googleOAuth2Service.loginWithGoogle(validRequest);

        // Then
        assertNotNull(response);
        assertEquals("HR", response.getRole());
        assertEquals("hr_bich", response.getUsername());
        assertEquals("GOOGLE", activeHrAccount.getAuthProvider());
        verify(accountRepository).save(activeHrAccount);
    }

    @Test
    @DisplayName("UT-BE-04: Gửi Google ID Token không hợp lệ / sai chữ ký -> Ném UnauthorizedException (HTTP 401)")
    void givenInvalidGoogleIdToken_whenLoginWithGoogle_thenThrowUnauthorizedException() {
        // Given
        when(tokenVerifierService.verify(validRequest.getIdToken()))
                .thenThrow(new UnauthorizedException("Google ID Token không hợp lệ hoặc đã hết hạn"));

        // When & Then
        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                googleOAuth2Service.loginWithGoogle(validRequest)
        );
        assertEquals("Google ID Token không hợp lệ hoặc đã hết hạn", ex.getMessage());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("UT-BE-05: Gửi Google ID Token có email_verified = false -> Ném BadRequestException (HTTP 400)")
    void givenUnverifiedGoogleEmail_whenLoginWithGoogle_thenThrowBadRequestException() {
        // Given
        GoogleUserInfo unverifiedUserInfo = GoogleUserInfo.builder()
                .email("unverified@gmail.com")
                .name("Unverified User")
                .emailVerified(false)
                .build();
        when(tokenVerifierService.verify(validRequest.getIdToken())).thenReturn(unverifiedUserInfo);

        // When & Then
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                googleOAuth2Service.loginWithGoogle(validRequest)
        );
        assertTrue(ex.getMessage().contains("chưa được xác minh"));
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("UT-BE-06: Gửi Google ID Token của tài khoản bị LOCKED -> Ném UnauthorizedException (HTTP 401)")
    void givenLockedAccount_whenLoginWithGoogle_thenThrowUnauthorizedException() {
        // Given
        when(tokenVerifierService.verify(validRequest.getIdToken())).thenReturn(validUserInfo);

        User existingUser = User.builder()
                .id(99)
                .fullName("Locked User")
                .email("cuong.le@gmail.com")
                .build();
        when(userRepository.findByEmail("cuong.le@gmail.com")).thenReturn(Optional.of(existingUser));

        Account lockedAccount = Account.builder()
                .id(199)
                .userId(99)
                .username("locked_user")
                .status("LOCKED")
                .role(internRole)
                .build();
        when(accountRepository.findByUserId(99)).thenReturn(Optional.of(lockedAccount));

        // When & Then
        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                googleOAuth2Service.loginWithGoogle(validRequest)
        );
        assertTrue(ex.getMessage().contains("đã bị khóa"));
        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("UT-BE-07: Tiền tố email bị trùng lặp username trong DB -> Tự động sinh hậu tố ngẫu nhiên duy nhất")
    void givenDuplicateUsernamePrefix_whenProvisioning_thenGenerateUniqueSuffix() {
        // Given
        when(tokenVerifierService.verify(validRequest.getIdToken())).thenReturn(validUserInfo);
        when(userRepository.findByEmail("cuong.le@gmail.com")).thenReturn(Optional.empty());

        User savedUser = User.builder().id(105).email("cuong.le@gmail.com").fullName("Lê Cường").build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Lần 1: cuongle đã tồn tại -> exists = true
        // Lần 2: cuongle_xxxx không tồn tại -> exists = false
        when(accountRepository.existsByUsername("cuongle")).thenReturn(true);
        when(accountRepository.existsByUsername(startsWith("cuongle_"))).thenReturn(false);

        when(roleRepository.findByName("Intern")).thenReturn(Optional.of(internRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.generateToken(any(Account.class))).thenReturn("token");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(3600L);

        // When
        LoginResponse response = googleOAuth2Service.loginWithGoogle(validRequest);

        // Then
        assertNotNull(response);
        assertTrue(response.getUsername().startsWith("cuongle_"), "Username mới phải chứa hậu tố phân biệt khi bị trùng");
        verify(accountRepository, atLeast(2)).existsByUsername(anyString());
    }

    @Test
    @DisplayName("UT-BE-08: Payload chứa Google ID Token khi qua DataMaskingUtils -> idToken được mặt nạ hóa thành ******")
    void givenGoogleLoginPayload_whenAuditing_thenEnsureIdTokenIsMaskedWithAsterisks() {
        // Given
        GoogleLoginRequest sensitiveRequest = GoogleLoginRequest.builder()
                .idToken("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.VERY_SENSITIVE_SECRET_TOKEN")
                .build();

        // When
        String maskedJson = DataMaskingUtils.maskObject(sensitiveRequest);

        // Then
        assertNotNull(maskedJson);
        assertTrue(maskedJson.contains("\"idToken\":\"******\"") || maskedJson.contains("\"idToken\": \"******\""),
                "idToken phải được mặt nạ hóa thành ****** để chống rò rỉ trên hệ thống log kiểm toán");
        assertFalse(maskedJson.contains("VERY_SENSITIVE_SECRET_TOKEN"),
                "Tuyệt đối không để lộ chuỗi token bí mật trong payload ghi log");
    }
}
