package org.example.employeeservice.oauth2.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.dto.response.LoginResponse;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.entity.User;
import org.example.employeeservice.exception.BadRequestException;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.oauth2.dto.request.GoogleLoginRequest;
import org.example.employeeservice.oauth2.dto.response.GoogleUserInfo;
import org.example.employeeservice.oauth2.service.GoogleOAuth2Service;
import org.example.employeeservice.oauth2.service.GoogleTokenVerifierService;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.example.employeeservice.repository.UserRepository;
import org.example.employeeservice.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai nghiệp vụ đăng nhập bằng tài khoản Google OAuth2.
 * Đảm bảo liên kết tài khoản tự động, cấp phát định danh an toàn và phòng chống các rủi ro bảo mật.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuth2ServiceImpl implements GoogleOAuth2Service {

    private static final String DEFAULT_ROLE_INTERN = "Intern";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING_ACTIVATION = "PENDING_ACTIVATION";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String AUTH_PROVIDER_GOOGLE = "GOOGLE";

    private final GoogleTokenVerifierService tokenVerifierService;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        log.info("Bắt đầu xử lý đăng nhập Google OAuth2");

        // 1. Xác thực ID Token qua Google Public Certificates
        GoogleUserInfo userInfo = tokenVerifierService.verify(request.getIdToken());

        // 2. Case 3: Kiểm tra cờ email_verified
        if (!userInfo.isEmailVerified()) {
            log.warn("Tài khoản Google {} chưa được xác minh hòm thư", userInfo.getEmail());
            throw new BadRequestException("Địa chỉ email Google chưa được xác minh. Vui lòng xác thực tài khoản Google trước khi tiếp tục.");
        }

        String email = userInfo.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Không trích xuất được email từ Google ID Token");
            throw new BadRequestException("Không tìm thấy địa chỉ email hợp lệ trong tài khoản Google");
        }
        email = email.trim().toLowerCase();

        // 3. Tra cứu hoặc đồng bộ thực thể User & Account
        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        UserSyncResult syncResult = existingUserOpt.isPresent()
                ? handleExistingUser(existingUserOpt.get(), userInfo)
                : handleNewUser(email, userInfo);

        Account account = syncResult.account();
        User user = syncResult.user();

        // 4. Sinh JWT Token nội bộ
        String token = jwtTokenProvider.generateToken(account);
        String roleName = (account.getRole() != null) ? account.getRole().getName() : DEFAULT_ROLE_INTERN;
        String fullName = (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : account.getUsername();

        log.info("Đăng nhập Google thành công cho username={}, vai trò={}", account.getUsername(), roleName);

        // 5. Chuẩn hóa LoginResponse DTO (loại trừ mọi PII nhạy cảm)
        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .username(account.getUsername())
                .fullName(fullName)
                .email(email)
                .role(roleName)
                .userId(user.getId())
                .build();
    }

    private UserSyncResult handleExistingUser(User user, GoogleUserInfo userInfo) {
        log.info("Phát hiện email đã tồn tại (userId={}), tiến hành liên kết tài khoản", user.getId());
        Account account = accountRepository.findByUserId(user.getId()).orElse(null);

        if (account != null) {
            // Case 5: Kiểm tra tài khoản bị khóa
            if (STATUS_LOCKED.equalsIgnoreCase(account.getStatus())) {
                log.warn("Tài khoản {} đang ở trạng thái LOCKED, từ chối đăng nhập", account.getUsername());
                throw new UnauthorizedException("Tài khoản của bạn đã bị khóa hoặc vô hiệu hóa bởi Quản trị viên");
            }

            // Chuyển PENDING_ACTIVATION ➔ ACTIVE
            if (STATUS_PENDING_ACTIVATION.equalsIgnoreCase(account.getStatus())) {
                log.info("Kích hoạt tài khoản {} từ PENDING_ACTIVATION sang ACTIVE qua xác thực Google", account.getUsername());
                account.setStatus(STATUS_ACTIVE);
            }

            account.setAuthProvider(AUTH_PROVIDER_GOOGLE);
            if (userInfo.getSub() != null) {
                account.setProviderId(userInfo.getSub());
            }
            account.setLastLoginAt(LocalDateTime.now());
            account = accountRepository.save(account);
        } else {
            account = provisionAccountForUser(user, user.getEmail(), userInfo.getSub());
        }

        // Đồng bộ avatar nếu hiện tại người dùng chưa có
        if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) && userInfo.getPicture() != null) {
            user.setAvatarUrl(userInfo.getPicture());
            userRepository.save(user);
        }

        return new UserSyncResult(user, account);
    }

    private UserSyncResult handleNewUser(String email, GoogleUserInfo userInfo) {
        log.info("Email {} chưa tồn tại, tự động khởi tạo hồ sơ User và Account mới", email);

        String displayName = (userInfo.getName() != null && !userInfo.getName().isBlank())
                ? userInfo.getName().trim()
                : email.split("@")[0];

        User newUser = User.builder()
                .fullName(displayName)
                .email(email)
                .avatarUrl(userInfo.getPicture())
                .build();
        User savedUser = userRepository.save(newUser);
        Account savedAccount = provisionAccountForUser(savedUser, email, userInfo.getSub());

        return new UserSyncResult(savedUser, savedAccount);
    }

    private Account provisionAccountForUser(User user, String email, String providerId) {
        String username = generateUniqueUsername(email);

        Role internRole = roleRepository.findByName(DEFAULT_ROLE_INTERN)
                .or(() -> roleRepository.findByName("ROLE_" + DEFAULT_ROLE_INTERN.toUpperCase()))
                .orElseGet(() -> roleRepository.save(Role.builder().name(DEFAULT_ROLE_INTERN).build()));

        String secureRandomPassword = UUID.randomUUID().toString();
        String passwordHash = passwordEncoder.encode(secureRandomPassword);

        Account account = Account.builder()
                .userId(user.getId())
                .username(username)
                .passwordHash(passwordHash)
                .role(internRole)
                .status(STATUS_ACTIVE)
                .authProvider(AUTH_PROVIDER_GOOGLE)
                .providerId(providerId)
                .lastLoginAt(LocalDateTime.now())
                .build();

        return accountRepository.save(account);
    }

    private String generateUniqueUsername(String email) {
        String rawPrefix = email.split("@")[0];
        String cleanPrefix = rawPrefix.replaceAll("\\W", "").toLowerCase();
        if (cleanPrefix.isBlank()) {
            cleanPrefix = "intern";
        }
        if (cleanPrefix.length() > 25) {
            cleanPrefix = cleanPrefix.substring(0, 25);
        }

        String candidate = cleanPrefix;
        while (accountRepository.existsByUsername(candidate)) {
            String randomHex = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
            candidate = cleanPrefix + "_" + randomHex;
        }

        return candidate;
    }

    private record UserSyncResult(User user, Account account) {}
}
