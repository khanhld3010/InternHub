package org.example.employeeservice.service;

import java.time.LocalDate;
import java.util.Optional;

import org.example.employeeservice.dto.request.RegisterRequest;
import org.example.employeeservice.dto.response.RegisterResponse;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.entity.User;
import org.example.employeeservice.entity.enums.Gender;
import org.example.employeeservice.exception.DuplicateResourceException;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest validRequest;
    private Role internRole;

    @BeforeEach
    void setUp() {
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
    @DisplayName("Đăng ký thành công: Lưu User trước rồi lưu Account liên kết với userId")
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
                .status("ACTIVE")
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
        assertEquals("Intern", response.getRole());
        assertEquals("ACTIVE", response.getStatus());

        // Xác minh thứ tự gọi: User được lưu trước, Account được lưu sau với đúng userId
        verify(userRepository, times(1)).save(any(User.class));
        verify(accountRepository, times(1)).save(argThat(acc -> acc.getUserId().equals(15) && acc.getUsername().equals("nguyenvana")));
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
    @DisplayName("Đăng ký thất bại khi email đã tồn tại")
    void givenDuplicateEmail_whenRegister_thenThrowDuplicateResourceException() {
        when(accountRepository.existsByUsername("nguyenvana")).thenReturn(false);
        when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> authService.register(validRequest)
        );

        assertTrue(exception.getMessage().contains("Email đã được sử dụng"));
        verify(userRepository, never()).save(any(User.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Đăng ký thất bại khi số điện thoại đã tồn tại")
    void givenDuplicatePhoneNumber_whenRegister_thenThrowDuplicateResourceException() {
        when(accountRepository.existsByUsername("nguyenvana")).thenReturn(false);
        when(userRepository.existsByEmail("nguyenvana@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("0987654321")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> authService.register(validRequest)
        );

        assertTrue(exception.getMessage().contains("Số điện thoại đã được sử dụng"));
        verify(userRepository, never()).save(any(User.class));
        verify(accountRepository, never()).save(any(Account.class));
    }
}
