package org.example.employeeservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.example.employeeservice.exception.BadRequestException;
import org.example.employeeservice.exception.DuplicateResourceException;
import org.example.employeeservice.exception.UnauthorizedException;
import org.example.employeeservice.repository.AccountActivationTokenRepository;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.example.employeeservice.repository.UserRepository;
import org.example.employeeservice.security.JwtTokenProvider;
import org.example.employeeservice.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountActivationTokenRepository activationTokenRepository;
    private final IntegrationEmailClient integrationEmailClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.activation.expiration-minutes:${ACTIVATION_TOKEN_EXPIRATION_MINUTES:15}}")
    private int expirationMinutes;

    @Value("${app.activation.max-attempts:${ACTIVATION_MAX_ATTEMPTS:5}}")
    private int maxAttempts;

    @Value("${app.activation.resend-cooldown-seconds:${ACTIVATION_RESEND_COOLDOWN_SECONDS:60}}")
    private int resendCooldownSeconds;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = request.getUsername().trim();
        log.info("Xử lý đăng nhập cho tài khoản: {}", identifier);

        Account account;
        if (identifier.contains("@")) {
            User user = userRepository.findByEmail(identifier.toLowerCase()).orElse(null);
            account = (user != null) ? accountRepository.findByUserId(user.getId()).orElse(null) : null;
        } else {
            account = accountRepository.findByUsername(identifier).orElse(null);
        }

        if (account == null) {
            log.warn("Không tìm thấy tài khoản với identifier: {}", identifier);
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            log.warn("Tài khoản {} không ở trạng thái ACTIVE (trạng thái: {})", account.getUsername(), account.getStatus());
            throw new UnauthorizedException("Tài khoản chưa được kích hoạt qua email hoặc đang bị khóa");
        }

        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            log.warn("Mật khẩu không khớp cho tài khoản: {}", account.getUsername());
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        account.setLastLoginAt(LocalDateTime.now());
        accountRepository.save(account);

        String token = jwtTokenProvider.generateToken(account);
        String roleName = (account.getRole() != null) ? account.getRole().getName() : "USER";

        User user = account.getUser();
        if (user == null && account.getUserId() != null) {
            user = userRepository.findById(account.getUserId()).orElse(null);
        }

        String fullName = (user != null && user.getFullName() != null) ? user.getFullName() : account.getUsername();
        String email = (user != null) ? user.getEmail() : null;

        log.info("Đăng nhập thành công cho tài khoản: {}, vai trò: {}", account.getUsername(), roleName);

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .username(account.getUsername())
                .fullName(fullName)
                .email(email)
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

        // 2. Phân vai trò
        String targetRoleName = "Intern";
        if (request.getRole() != null && !request.getRole().isBlank()) {
            if ("MENTOR".equalsIgnoreCase(request.getRole())) {
                targetRoleName = "Mentor";
            } else if ("INTERN".equalsIgnoreCase(request.getRole())) {
                targetRoleName = "Intern";
            }
        }
        final String roleNameToFind = targetRoleName;
        Role assignedRole = roleRepository.findByName(roleNameToFind)
                .or(() -> roleRepository.findByName("ROLE_" + roleNameToFind.toUpperCase()))
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleNameToFind).build()));

        // 3. Pha 1: Lưu thông tin cá nhân vào bảng users
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

        // 4. Pha 2: Lưu bảng accounts với trạng thái PENDING_ACTIVATION (TM-10 v1.4)
        Account account = Account.builder()
                .userId(savedUser.getId())
                .user(savedUser)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .status("PENDING_ACTIVATION")
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("Pha 2 hoàn thành: Đã lưu bảng accounts với ID={}, username={}, status={}",
                savedAccount.getId(), username, savedAccount.getStatus());

        // 5. Pha 3: Sinh mã kích hoạt OTP 6 số an toàn và lưu DB
        String activationKey = generateOtp();
        AccountActivationToken activationToken = AccountActivationToken.builder()
                .accountId(savedAccount.getId())
                .activationKey(activationKey)
                .tokenType("REGISTER_ACTIVATION")
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .attemptCount(0)
                .build();

        activationTokenRepository.save(activationToken);
        log.info("Pha 3 hoàn thành: Đã tạo mã OTP kích hoạt tài khoản cho accountId={}", savedAccount.getId());

        // 6. Pha 4: Gửi email xác thực thông qua Reporting Service
        integrationEmailClient.sendActivationEmail(
                savedUser.getEmail(),
                savedUser.getFullName(),
                activationKey,
                expirationMinutes
        );

        // 7. Pha 5: Đóng gói phản hồi, ẩn hoàn toàn mã OTP khỏi Client
        return RegisterResponse.builder()
                .userId(savedUser.getId())
                .username(savedAccount.getUsername())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .maskedEmail(maskEmail(savedUser.getEmail()))
                .phoneNumber(savedUser.getPhoneNumber())
                .role(assignedRole.getName())
                .status(savedAccount.getStatus())
                .createdAt(savedAccount.getCreatedAt() != null ? savedAccount.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateAccount(ActivateAccountRequest request) {
        String identifier = request.getIdentifier().trim();
        String activationKey = request.getActivationKey().trim();

        log.info("Xử lý kích hoạt tài khoản cho identifier: {}", identifier);

        Account account = findAccountByIdentifier(identifier);

        if ("ACTIVE".equalsIgnoreCase(account.getStatus())) {
            log.info("Tài khoản {} đã ở trạng thái ACTIVE trước đó", account.getUsername());
            return;
        }

        // Lấy token kích hoạt mới nhất chưa sử dụng
        AccountActivationToken token = activationTokenRepository
                .findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(account.getId())
                .orElseThrow(() -> new BadRequestException(
                        "Không tìm thấy yêu cầu kích hoạt hợp lệ hoặc mã đã được sử dụng. Vui lòng yêu cầu gửi lại mã mới."));

        // Kiểm tra số lần thử sai (chống Brute-force)
        if (token.getAttemptCount() >= maxAttempts) {
            log.warn("Tài khoản {} đã nhập sai OTP quá {} lần", account.getUsername(), maxAttempts);
            throw new BadRequestException("Mã kích hoạt đã bị khóa do nhập sai quá " + maxAttempts + " lần. Vui lòng yêu cầu gửi lại mã mới.");
        }

        // Kiểm tra thời hạn hiệu lực (TTL)
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Mã kích hoạt của tài khoản {} đã hết hạn lúc {}", account.getUsername(), token.getExpiresAt());
            throw new BadRequestException("Mã kích hoạt đã hết hạn. Vui lòng bấm 'Gửi lại mã' để nhận mã mới.");
        }

        // So khớp mã kích hoạt
        if (!token.getActivationKey().equals(activationKey)) {
            token.setAttemptCount(token.getAttemptCount() + 1);
            activationTokenRepository.save(token);
            int remaining = maxAttempts - token.getAttemptCount();
            log.warn("Mã kích hoạt không đúng cho tài khoản {}. Số lần còn lại: {}", account.getUsername(), remaining);
            if (remaining <= 0) {
                throw new BadRequestException("Mã kích hoạt không chính xác. Bạn đã hết số lần thử. Vui lòng yêu cầu gửi lại mã mới.");
            }
            throw new BadRequestException("Mã kích hoạt không chính xác. Bạn còn " + remaining + " lần thử.");
        }

        // Kích hoạt thành công
        token.setConsumedAt(LocalDateTime.now());
        activationTokenRepository.save(token);

        account.setStatus("ACTIVE");
        accountRepository.save(account);

        log.info("Kích hoạt tài khoản thành công cho username={}", account.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resendActivation(ResendActivationRequest request) {
        String identifier = request.getIdentifier().trim();
        log.info("Xử lý yêu cầu gửi lại mã kích hoạt cho: {}", identifier);

        Account account = findAccountByIdentifier(identifier);

        if ("ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new BadRequestException("Tài khoản này đã được kích hoạt trước đó. Bạn có thể đăng nhập ngay.");
        }

        // Kiểm tra Cooldown chống spam mail
        Optional<AccountActivationToken> latestTokenOpt = activationTokenRepository
                .findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(account.getId());

        if (latestTokenOpt.isPresent()) {
            AccountActivationToken latest = latestTokenOpt.get();
            LocalDateTime createdAt = latest.getCreatedAt() != null ? latest.getCreatedAt() : LocalDateTime.now().minusMinutes(2);
            long secondsElapsed = Duration.between(createdAt, LocalDateTime.now()).getSeconds();
            if (secondsElapsed < resendCooldownSeconds) {
                long waitSeconds = resendCooldownSeconds - secondsElapsed;
                log.warn("Yêu cầu gửi lại mã quá nhanh cho tài khoản {}. Còn lại {}s", account.getUsername(), waitSeconds);
                throw new BadRequestException("Vui lòng đợi " + waitSeconds + " giây trước khi yêu cầu gửi lại mã kích hoạt mới.");
            }
        }

        // Hủy các mã cũ chưa tiêu thụ
        List<AccountActivationToken> oldTokens = activationTokenRepository.findByAccountIdAndConsumedAtIsNull(account.getId());
        for (AccountActivationToken old : oldTokens) {
            old.setConsumedAt(LocalDateTime.now());
        }
        activationTokenRepository.saveAll(oldTokens);

        // Sinh mã mới
        String newActivationKey = generateOtp();
        AccountActivationToken newToken = AccountActivationToken.builder()
                .accountId(account.getId())
                .activationKey(newActivationKey)
                .tokenType("REGISTER_ACTIVATION")
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .attemptCount(0)
                .build();

        activationTokenRepository.save(newToken);

        // Lấy thông tin email và họ tên người dùng để gửi mail
        User user = account.getUser();
        if (user == null && account.getUserId() != null) {
            user = userRepository.findById(account.getUserId()).orElse(null);
        }

        String targetEmail = (user != null && user.getEmail() != null) ? user.getEmail() : identifier;
        String targetFullName = (user != null && user.getFullName() != null) ? user.getFullName() : account.getUsername();

        integrationEmailClient.sendActivationEmail(
                targetEmail,
                targetFullName,
                newActivationKey,
                expirationMinutes
        );

        log.info("Đã phát mã OTP mới cho tài khoản: {}, email: {}", account.getUsername(), targetEmail);
    }

    private Account findAccountByIdentifier(String identifier) {
        if (identifier.contains("@")) {
            User user = userRepository.findByEmail(identifier.toLowerCase())
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin tài khoản với email: " + identifier));
            return accountRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy tài khoản liên kết với email này"));
        } else {
            return accountRepository.findByUsername(identifier)
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy tài khoản với tên đăng nhập: " + identifier));
        }
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 3) + "***" + domain;
    }
}
