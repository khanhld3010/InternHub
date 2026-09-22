package org.example.employeeservice.config;

import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;

/**
 * Khởi tạo dữ liệu mặc định (Users, Roles và Accounts) chuẩn hóa theo Database hiện tại (internhub_db).
 * Users: 6 tài khoản người dùng mẫu
 * Roles: Admin, HR, Mentor, Intern
 * Accounts tương ứng với Users trong DB:
 *  - admin  (userId = 6: Lưu Đức Khánh - Quản trị viên)
 *  - hr     (userId = 2: Trần Thị Bích - Tuyển dụng / HR)
 *  - mentor (userId = 3: Lê Hoàng Nam - Người hướng dẫn)
 *  - intern (userId = 4: Phạm Đức Minh - Thực tập sinh)
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DataInitializer(
            RoleRepository roleRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this.roleRepository = roleRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DataInitializer(
            RoleRepository roleRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this(roleRepository, accountRepository, passwordEncoder, null);
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Bắt đầu kiểm tra và đồng bộ dữ liệu mặc định theo DB hiện tại...");

        // 1. Khởi tạo bảng users và người dùng mẫu nếu chưa có
        initializeUsersIfNotExist();

        // 2. Khởi tạo danh sách vai trò (Roles) chuẩn theo DB: Admin, HR, Mentor, Intern
        Role adminRole = getOrCreateRole("Admin");
        Role hrRole = getOrCreateRole("HR");
        Role mentorRole = getOrCreateRole("Mentor");
        Role internRole = getOrCreateRole("Intern");

        // 3. Khởi tạo tài khoản mẫu (Accounts) với mật khẩu mặc định "123456"
        createAccountIfNotExist("admin", "123456", 6, adminRole);
        createAccountIfNotExist("hr", "123456", 2, hrRole);
        createAccountIfNotExist("mentor", "123456", 3, mentorRole);
        createAccountIfNotExist("intern", "123456", 4, internRole);

        printSummary();
    }

    private void initializeUsersIfNotExist() {
        if (jdbcTemplate == null) {
            log.debug("JdbcTemplate không khả dụng, bỏ qua khởi tạo bảng users.");
            return;
        }

        try {
            // Đảm bảo bảng users tồn tại nếu chạy trên DB mới hoặc shared DB
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    full_name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) NOT NULL UNIQUE,
                    phone_number VARCHAR(20) UNIQUE,
                    date_of_birth DATE,
                    gender VARCHAR(10),
                    address VARCHAR(255),
                    avatar_url VARCHAR(500),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            jdbcTemplate.execute(createTableSql);

            // Bổ sung các user mẫu chuẩn theo DB nếu chưa tồn tại
            insertUserIfNotExist(1, "Nguyễn Văn An", "admin@internhub.com", "0901112233", "1990-05-15", "MALE", "Hà Nội", "https://api.dicebear.com/7.x/avataaars/svg?seed=An");
            insertUserIfNotExist(2, "Trần Thị Bích", "bich.tran@internhub.com", "0912223344", "1995-10-20", "FEMALE", "Đà Nẵng", "https://api.dicebear.com/7.x/avataaars/svg?seed=Bich");
            insertUserIfNotExist(3, "Lê Hoàng Nam", "nam.le@internhub.com", "0923334455", "1992-03-12", "MALE", "TP. Hồ Chí Minh", "https://api.dicebear.com/7.x/avataaars/svg?seed=Nam");
            insertUserIfNotExist(4, "Phạm Đức Minh", "minh.pham@gmail.com", "0934445566", "2002-08-25", "MALE", "Hà Nội", "https://api.dicebear.com/7.x/avataaars/svg?seed=Minh");
            insertUserIfNotExist(5, "Hoàng Thị Mai", "mai.hoang@gmail.com", "0945556677", "2003-12-05", "FEMALE", "Cần Thơ", "https://api.dicebear.com/7.x/avataaars/svg?seed=Mai");
            insertUserIfNotExist(6, "Lưu Đức Khánh", "luuduckhanh@gmail.com", "0969891732", "2003-10-30", "MALE", "Hà Nội", "https://api.dicebear.com/7.x/avataaars/svg?seed=khanh");

        } catch (Exception e) {
            log.warn("Không thể kiểm tra hoặc khởi tạo bảng users qua JdbcTemplate: {}", e.getMessage());
        }
    }

    private void insertUserIfNotExist(
            int id,
            String fullName,
            String email,
            String phoneNumber,
            String dateOfBirth,
            String gender,
            String address,
            String avatarUrl
    ) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? OR email = ?",
                Integer.class,
                id,
                email
        );
        if (count == null || count == 0) {
            Date dob = (dateOfBirth != null) ? Date.valueOf(dateOfBirth) : null;
            jdbcTemplate.update(
                    """
                    INSERT INTO users (id, full_name, email, phone_number, date_of_birth, gender, address, avatar_url, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    id, fullName, email, phoneNumber, dob, gender, address, avatarUrl
            );
            log.info("-> Đã khởi tạo User [ID: {}]: {} ({})", id, fullName, email);
        }
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
