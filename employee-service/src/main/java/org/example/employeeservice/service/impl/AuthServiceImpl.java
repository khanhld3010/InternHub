package org.example.employeeservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.dto.request.LoginRequest;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.repository.AccountRepository;
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
}
