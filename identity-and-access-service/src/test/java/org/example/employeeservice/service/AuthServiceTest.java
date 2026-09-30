package org.example.employeeservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import org.example.employeeservice.client.IntegrationEmailClient;
import org.example.employeeservice.dto.request.ActivateAccountRequest;
import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.request.RegisterRequest;
import org.example.employeeservice.dto.request.ResendActivationRequest;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.dto.response.RegisterResponse;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.AccountActivationToken;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.entity.User;
import org.example.employeeservice.entity.enums.Gender;
import org.example.employeeservice.exception.BadRequestException;
import org.example.employeeservice.exception.DuplicateResourceException;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.repository.AccountActivationTokenRepository;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.example.employeeservice.repository.UserRepository;
import org.example.employeeservice.security.JwtTokenProvider;
import org.example.employeeservice.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AccountActivationTokenRepository activationTokenRepository;

    @Mock
    private IntegrationEmailClient integrationEmailClient;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest validRequest;
    private Role internRole;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "expirationMinutes", 15);
        ReflectionTestUtils.setField(authService, "maxAttempts", 5);
        ReflectionTestUtils.setField(authService, "resendCooldownSeconds", 60);

        validRequest = RegisterRequest.builder()
                .username("nguyenvana")
                .password("Password123@")
                .fullName("Nguyễn Văn A")
                .email("nguyenvana@gmail.com")
                .phoneNumber("0987654321")
                .dateOfBirth(LocalDate.of(2003, 5, 15))
                .gender(Gender.MALE)
                .address("Hà Nội")
                .build();

        internRole = Role.builder()
                .id(4)
                .name("Intern")
                .build();
    }

    @Test
    @DisplayName("TM-10 v1.4 Đăng ký thành công: status=PENDING_ACTIVATION, sinh OTP và gọi EmailClient")
    void givenValidCompositeRequest_whenRegister_thenExtractAndSaveUserThenAccountSuccess() {
        // Arrange
        when(accountRepository.existsByUsername("nguyenvana")).thenReturn(false);
        when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("0987654321")).thenReturn(false);
        when(roleRepository.findByName("Intern")).thenReturn(Optional.of(internRole));

        User savedUser = User.builder()
                .id(15)
                .fullName(validRequest.getFullName())
                .email(validRequest.getEmail())
                .phoneNumber(validRequest.getPhoneNumber())
                .dateOfBirth(validRequest.getDateOfBirth())
                .gender(validRequest.getGender())
                .address(validRequest.getAddress())
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(passwordEncoder.encode("Password123@")).thenReturn("hashedPassword123");

        Account savedAccount = Account.builder()
                .id(10)
                .userId(15)
                .username("nguyenvana")
                .passwordHash("hashedPassword123")
                .role(internRole)
                .status("PENDING_ACTIVATION")
                .build();

        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        // Act
        RegisterResponse response = authService.register(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals(15, response.getUserId());
        assertEquals("nguyenvana", response.getUsername());
        assertEquals("Nguyễn Văn A", response.getFullName());
        assertEquals("nguyenvana@gmail.com", response.getEmail());
        assertEquals("ngu***@gmail.com", response.getMaskedEmail());
        assertEquals("Intern", response.getRole());
        assertEquals("PENDING_ACTIVATION", response.getStatus());

        // Xác minh lưu DB và gọi EmailClient
        verify(userRepository, times(1)).save(any(User.class));
        verify(accountRepository, times(1)).save(argThat(acc -> 
                acc.getUserId().equals(15) && 
                acc.getUsername().equals("nguyenvana") && 
                "PENDING_ACTIVATION".equals(acc.getStatus())));
        verify(activationTokenRepository, times(1)).save(any(AccountActivationToken.class));
        verify(integrationEmailClient, times(1)).sendActivationEmail(
                eq("nguyenvana@gmail.com"), eq("Nguyễn Văn A"), anyString(), eq(15));
    }

    @Test
    @DisplayName("Đăng ký thất bại khi username đã tồn tại")
    void givenDuplicateUsername_whenRegister_thenThrowDuplicateResourceException() {
        when(accountRepository.existsByUsername("nguyenvana")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> authService.register(validRequest)
        );

        assertTrue(exception.getMessage().contains("Tên đăng nhập đã tồn tại"));
        verify(userRepository, never()).save(any(User.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("TM-10 v1.4 Kích hoạt tài khoản thành công khi nhập đúng OTP")
    void givenValidOtp_whenActivateAccount_thenStatusChangesToActive() {
        // Arrange
        Account account = Account.builder()
                .id(10)
                .username("nguyenvana")
                .status("PENDING_ACTIVATION")
                .build();

        when(accountRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(account));

        AccountActivationToken token = AccountActivationToken.builder()
                .id(1L)
                .accountId(10)
                .activationKey("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .attemptCount(0)
                .build();

        when(activationTokenRepository.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(10))
                .thenReturn(Optional.of(token));

        ActivateAccountRequest request = ActivateAccountRequest.builder()
                .identifier("nguyenvana")
                .activationKey("123456")
                .build();

        // Act
        authService.activateAccount(request);

        // Assert
        assertEquals("ACTIVE", account.getStatus());
        assertNotNull(token.getConsumedAt());
        verify(accountRepository, times(1)).save(account);
        verify(activationTokenRepository, times(1)).save(token);
    }

    @Test
    @DisplayName("TM-10 v1.4 Kích hoạt tài khoản thất bại khi nhập sai OTP")
    void givenInvalidOtp_whenActivateAccount_thenIncrementAttemptCountAndThrow() {
        // Arrange
        Account account = Account.builder()
                .id(10)
                .username("nguyenvana")
                .status("PENDING_ACTIVATION")
                .build();

        when(accountRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(account));

        AccountActivationToken token = AccountActivationToken.builder()
                .id(1L)
                .accountId(10)
                .activationKey("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .attemptCount(1)
                .build();

        when(activationTokenRepository.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(10))
                .thenReturn(Optional.of(token));

        ActivateAccountRequest request = ActivateAccountRequest.builder()
                .identifier("nguyenvana")
                .activationKey("999999")
                .build();

        // Act & Assert
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> authService.activateAccount(request)
        );

        assertTrue(exception.getMessage().contains("Mã kích hoạt không chính xác"));
        assertEquals(2, token.getAttemptCount());
        verify(activationTokenRepository, times(1)).save(token);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("TM-10 v1.4 Kích hoạt tài khoản thất bại khi nhập sai quá 5 lần (Brute-force)")
    void givenExceededMaxAttempts_whenActivateAccount_thenThrowException() {
        Account account = Account.builder()
                .id(10)
                .username("nguyenvana")
                .status("PENDING_ACTIVATION")
                .build();

        when(accountRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(account));

        AccountActivationToken token = AccountActivationToken.builder()
                .id(1L)
                .accountId(10)
                .activationKey("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .attemptCount(5)
                .build();

        when(activationTokenRepository.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(10))
                .thenReturn(Optional.of(token));

        ActivateAccountRequest request = ActivateAccountRequest.builder()
                .identifier("nguyenvana")
                .activationKey("123456")
                .build();

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> authService.activateAccount(request)
        );

        assertTrue(exception.getMessage().contains("nhập sai quá 5 lần"));
    }

    @Test
    @DisplayName("TM-10 v1.4 Kích hoạt tài khoản thất bại khi mã OTP đã hết hạn")
    void givenExpiredOtp_whenActivateAccount_thenThrowException() {
        Account account = Account.builder()
                .id(10)
                .username("nguyenvana")
                .status("PENDING_ACTIVATION")
                .build();

        when(accountRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(account));

        AccountActivationToken token = AccountActivationToken.builder()
                .id(1L)
                .accountId(10)
                .activationKey("123456")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .attemptCount(0)
                .build();

        when(activationTokenRepository.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(10))
                .thenReturn(Optional.of(token));

        ActivateAccountRequest request = ActivateAccountRequest.builder()
                .identifier("nguyenvana")
                .activationKey("123456")
                .build();

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> authService.activateAccount(request)
        );

        assertTrue(exception.getMessage().contains("Mã kích hoạt đã hết hạn"));
    }

    @Test
    @DisplayName("TM-10 v1.4 Gửi lại mã kích hoạt thành công sau thời gian Cooldown")
    void givenValidCooldown_whenResendActivation_thenGenerateNewOtpAndDispatchEmail() {
        User user = User.builder()
                .id(15)
                .email("nguyenvana@gmail.com")
                .fullName("Nguyễn Văn A")
                .build();

        Account account = Account.builder()
                .id(10)
                .userId(15)
                .username("nguyenvana")
                .status("PENDING_ACTIVATION")
                .user(user)
                .build();

        when(accountRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(account));

        AccountActivationToken oldToken = AccountActivationToken.builder()
                .id(1L)
                .accountId(10)
                .activationKey("111111")
                .createdAt(LocalDateTime.now().minusSeconds(70))
                .build();

        when(activationTokenRepository.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(10))
                .thenReturn(Optional.of(oldToken));
        when(activationTokenRepository.findByAccountIdAndConsumedAtIsNull(10))
                .thenReturn(Collections.singletonList(oldToken));

        ResendActivationRequest request = ResendActivationRequest.builder()
                .identifier("nguyenvana")
                .build();

        // Act
        authService.resendActivation(request);

        // Assert
        verify(activationTokenRepository, times(1)).saveAll(any());
        verify(activationTokenRepository, times(1)).save(any(AccountActivationToken.class));
        verify(integrationEmailClient, times(1)).sendActivationEmail(
                eq("nguyenvana@gmail.com"), eq("Nguyễn Văn A"), anyString(), eq(15));
    }

    @Test
    @DisplayName("TM-10 v1.4 Gửi lại mã thất bại khi chưa hết thời gian Cooldown 60s")
    void givenCooldownViolation_whenResendActivation_thenThrowException() {
        Account account = Account.builder()
                .id(10)
                .userId(15)
                .username("nguyenvana")
                .status("PENDING_ACTIVATION")
                .build();

        when(accountRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(account));

        AccountActivationToken recentToken = AccountActivationToken.builder()
                .id(1L)
                .accountId(10)
                .activationKey("111111")
                .createdAt(LocalDateTime.now().minusSeconds(20)) // Mới tạo 20s trước
                .build();

        when(activationTokenRepository.findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(10))
                .thenReturn(Optional.of(recentToken));

        ResendActivationRequest request = ResendActivationRequest.builder()
                .identifier("nguyenvana")
                .build();

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> authService.resendActivation(request)
        );

        assertTrue(exception.getMessage().contains("Vui lòng đợi"));
        verify(integrationEmailClient, never()).sendActivationEmail(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Đăng nhập thành công bằng username: trả về JWT token và thông tin người dùng")
    void givenValidUsernameAndPassword_whenLogin_thenReturnLoginResponse() {
        // Arrange
        User user = User.builder()
                .id(15)
                .fullName("Nguyễn Văn A")
                .email("nguyenvana@gmail.com")
                .build();

        Account account = Account.builder()
                .id(10)
                .userId(15)
                .username("nguyenvana")
                .passwordHash("$2a$10$hashedPassword")
                .status("ACTIVE")
                .role(internRole)
                .user(user)
                .build();

        when(accountRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Password123@", "$2a$10$hashedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateToken(account)).thenReturn("mocked.jwt.token");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(86400L);
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        LoginRequest request = LoginRequest.builder()
                .username("nguyenvana")
                .password("Password123@")
                .build();

        // Act
        LoginResponse response = authService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getAccessToken());
        assertEquals("nguyenvana", response.getUsername());
        assertEquals("Nguyễn Văn A", response.getFullName());
        assertEquals("nguyenvana@gmail.com", response.getEmail());
        assertEquals("Intern", response.getRole());
        assertEquals(15, response.getUserId());
        verify(accountRepository, times(1)).save(account);
    }

    @Test
    @DisplayName("Đăng nhập thành công bằng email: tự động tra cứu user và trả về JWT token")
    void givenValidEmailAndPassword_whenLogin_thenReturnLoginResponse() {
        // Arrange
        User user = User.builder()
                .id(15)
                .fullName("Nguyễn Văn A")
                .email("nguyenvana@gmail.com")
                .build();

        Account account = Account.builder()
                .id(10)
                .userId(15)
                .username("nguyenvana")
                .passwordHash("$2a$10$hashedPassword")
                .status("ACTIVE")
                .role(internRole)
                .user(user)
                .build();

        when(userRepository.findByEmail("nguyenvana@gmail.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByUserId(15)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Password123@", "$2a$10$hashedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateToken(account)).thenReturn("mocked.jwt.token");
        when(jwtTokenProvider.getExpirationInSeconds()).thenReturn(86400L);
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        LoginRequest request = LoginRequest.builder()
                .username("nguyenvana@gmail.com")
                .password("Password123@")
                .build();

        // Act
        LoginResponse response = authService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getAccessToken());
        assertEquals("nguyenvana", response.getUsername());
        assertEquals("Nguyễn Văn A", response.getFullName());
        assertEquals("nguyenvana@gmail.com", response.getEmail());
    }

    @Test
    @DisplayName("Đăng nhập thất bại: Tài khoản không tồn tại ném UnauthorizedException")
    void givenNonExistentUser_whenLogin_thenThrowUnauthorizedException() {
        when(accountRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

        LoginRequest request = LoginRequest.builder()
                .username("unknown_user")
                .password("Password123@")
                .build();

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(request)
        );

        assertEquals("Tên đăng nhập hoặc mật khẩu không chính xác", exception.getMessage());
    }

    @Test
    @DisplayName("Đăng nhập thất bại: Sai mật khẩu ném UnauthorizedException")
    void givenWrongPassword_whenLogin_thenThrowUnauthorizedException() {
        Account account = Account.builder()
                .id(10)
                .userId(15)
                .username("nguyenvana")
                .passwordHash("$2a$10$hashedPassword")
                .status("ACTIVE")
                .build();

        when(accountRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("WrongPass123", "$2a$10$hashedPassword")).thenReturn(false);

        LoginRequest request = LoginRequest.builder()
                .username("nguyenvana")
                .password("WrongPass123")
                .build();

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(request)
        );

        assertEquals("Tên đăng nhập hoặc mật khẩu không chính xác", exception.getMessage());
        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("Đăng nhập thất bại: Tài khoản PENDING_ACTIVATION ném UnauthorizedException")
    void givenInactiveAccount_whenLogin_thenThrowUnauthorizedException() {
        Account account = Account.builder()
                .id(10)
                .userId(15)
                .username("nguyenvana")
                .passwordHash("$2a$10$hashedPassword")
                .status("PENDING_ACTIVATION")
                .build();

        when(accountRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(account));

        LoginRequest request = LoginRequest.builder()
                .username("nguyenvana")
                .password("Password123@")
                .build();

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(request)
        );

        assertTrue(exception.getMessage().contains("chưa được kích hoạt"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
