package org.example.employeeservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khởi tạo dữ liệu mặc định (Roles và Accounts) chuẩn hóa theo Database hiện tại (internhub_db).
 * Roles: Admin, HR, Mentor, Intern
 * Accounts tương ứng với Users trong DB:
 *  - admin  (userId = 6: Lưu Đức Khánh - Quản trị viên)
 *  - hr     (userId = 2: Trần Thị Bích - Tuyển dụng / HR)
 *  - mentor (userId = 3: Lê Hoàng Nam - Người hướng dẫn)
 *  - intern (userId = 4: Phạm Đức Minh - Thực tập sinh)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Bắt đầu kiểm tra và đồng bộ dữ liệu mặc định theo DB hiện tại...");

        // 1. Khởi tạo danh sách vai trò (Roles) chuẩn theo DB: Admin, HR, Mentor, Intern
        Role adminRole = getOrCreateRole("Admin");
        Role hrRole = getOrCreateRole("HR");
        Role mentorRole = getOrCreateRole("Mentor");
        Role internRole = getOrCreateRole("Intern");

        // 2. Khởi tạo tài khoản mẫu (Accounts) với mật khẩu mặc định "123456"
        createAccountIfNotExist("admin", "123456", 6, adminRole);
        createAccountIfNotExist("hr", "123456", 2, hrRole);
        createAccountIfNotExist("mentor", "123456", 3, mentorRole);
        createAccountIfNotExist("intern", "123456", 4, internRole);

        printSummary();
    }

    private Role getOrCreateRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .name(roleName)
                            .build();
                    Role saved = roleRepository.save(newRole);
                    log.info("-> Đã khởi tạo Role: {}", roleName);
                    return saved;
                });
    }

    private void createAccountIfNotExist(String username, String rawPassword, Integer userId, Role role) {
        if (accountRepository.existsByUsername(username)) {
            log.debug("Tài khoản '{}' đã tồn tại, bỏ qua.", username);
            return;
        }

        if (accountRepository.existsByUserId(userId)) {
            log.warn("UserId '{}' đã được sử dụng cho tài khoản khác, bỏ qua tạo tài khoản '{}'.", userId, username);
            return;
        }

        Account account = Account.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .userId(userId)
                .role(role)
                .status("ACTIVE")
                .build();

        accountRepository.save(account);
        log.info("-> Đã khởi tạo Account: {} (Vai trò: {}, UserId: {})", username, role.getName(), userId);
    }

    private void printSummary() {
        log.info("============================================================================");
        log.info("🚀 INTERNHUB - KHỞI TẠO DỮ LIỆU BAN ĐẦU HOÀN TẤT");
        log.info("Danh sách tài khoản mẫu sẵn sàng kiểm thử (Mật khẩu mặc định: 123456):");
        log.info("  1. Username: admin   | Role: Admin  | UserId: 6 (Lưu Đức Khánh - Quản trị viên)");
        log.info("  2. Username: hr      | Role: HR     | UserId: 2 (Trần Thị Bích - Tuyển dụng / HR)");
        log.info("  3. Username: mentor  | Role: Mentor | UserId: 3 (Lê Hoàng Nam - Người hướng dẫn)");
        log.info("  4. Username: intern  | Role: Intern | UserId: 4 (Phạm Đức Minh - Thực tập sinh)");
        log.info("============================================================================");
    }
}
