package org.example.employeeservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.request.RegisterRequest;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.dto.response.RegisterResponse;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.entity.User;
import org.example.employeeservice.exception.DuplicateResourceException;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.example.employeeservice.repository.UserRepository;
import org.example.employeeservice.security.JwtTokenProvider;
import org.example.employeeservice.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        log.info("Xử lý đăng nhập cho tài khoản: {}", username);

        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Không tìm thấy tài khoản với username: {}", username);
                    return new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
                });

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            log.warn("Tài khoản {} không ở trạng thái ACTIVE (trạng thái: {})", username, account.getStatus());
            throw new UnauthorizedException("Tài khoản đang bị khóa hoặc chưa được kích hoạt");
        }

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            log.warn("Mật khẩu không khớp cho tài khoản: {}", username);
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        account.setLastLoginAt(LocalDateTime.now());
        accountRepository.save(account);

        String token = jwtTokenProvider.generateToken(account);
        String roleName = (account.getRole() != null) ? account.getRole().getName() : "USER";

        log.info("Đăng nhập thành công cho tài khoản: {}, vai trò: {}", username, roleName);

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .username(account.getUsername())
                .role(roleName)
                .userId(account.getUserId())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();
        String phoneNumber = request.getPhoneNumber().trim();

        log.info("Bắt đầu xử lý đăng ký tài khoản mới: username={}, email={}", username, email);

        // 1. Kiểm tra tính duy nhất
        if (accountRepository.existsByUsername(username)) {
            log.warn("Đăng ký thất bại: Tên đăng nhập {} đã tồn tại", username);
            throw new DuplicateResourceException("Tên đăng nhập đã tồn tại trong hệ thống");
        }

        if (userRepository.existsByEmail(email)) {
            log.warn("Đăng ký thất bại: Email {} đã tồn tại", email);
            throw new DuplicateResourceException("Email đã được sử dụng");
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            log.warn("Đăng ký thất bại: Số điện thoại {} đã tồn tại", phoneNumber);
            throw new DuplicateResourceException("Số điện thoại đã được sử dụng");
        }

        // 2. Tìm hoặc khởi tạo vai trò mặc định cho tài khoản đăng ký mới (Intern)
        Role internRole = roleRepository.findByName("Intern")
                .or(() -> roleRepository.findByName("ROLE_INTERN"))
                .orElseGet(() -> roleRepository.save(Role.builder().name("Intern").build()));

        // 3. Pha 1: Tách và lưu thông tin cá nhân vào bảng users
        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .phoneNumber(phoneNumber)
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .avatarUrl(request.getAvatarUrl() != null ? request.getAvatarUrl().trim() : null)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Pha 1 hoàn thành: Đã lưu bảng users với ID={}", savedUser.getId());

        // 4. Pha 2: Tách thông tin xác thực, liên kết userId và lưu vào bảng accounts
        Account account = Account.builder()
                .userId(savedUser.getId())
                .user(savedUser)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(internRole)
                .status("ACTIVE")
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("Pha 2 hoàn thành: Đã lưu bảng accounts với ID={}, username={}", savedAccount.getId(), username);

        return RegisterResponse.builder()
                .userId(savedUser.getId())
                .username(savedAccount.getUsername())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .phoneNumber(savedUser.getPhoneNumber())
                .role(internRole.getName())
                .status(savedAccount.getStatus())
                .createdAt(savedAccount.getCreatedAt() != null ? savedAccount.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}
