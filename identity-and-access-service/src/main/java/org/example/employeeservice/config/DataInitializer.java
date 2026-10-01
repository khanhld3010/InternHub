package org.example.employeeservice.config;

import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Permission;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.PermissionRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Khởi tạo dữ liệu mặc định (Users, Roles, Permissions và Accounts) chuẩn hóa theo Database hiện tại (internhub_db).
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final PermissionRepository permissionRepository;

    @Autowired
    public DataInitializer(
            RoleRepository roleRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            PermissionRepository permissionRepository
    ) {
        this.roleRepository = roleRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.permissionRepository = permissionRepository;
    }

    public DataInitializer(
            RoleRepository roleRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this(roleRepository, accountRepository, passwordEncoder, jdbcTemplate, null);
    }

    public DataInitializer(
            RoleRepository roleRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this(roleRepository, accountRepository, passwordEncoder, null, null);
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Bắt đầu kiểm tra và đồng bộ dữ liệu mặc định theo DB hiện tại...");

        // 1. Khởi tạo bảng users và người dùng mẫu nếu chưa có
        initializeUsersIfNotExist();

        // 2. Khởi tạo danh mục đặc quyền (Permissions) chuẩn hóa
        initializePermissionsIfNotExist();

        // 3. Khởi tạo danh sách vai trò (Roles) chuẩn theo DB: Admin, HR, Mentor, Intern
        Role adminRole = getOrCreateRole("Admin", "Quản trị viên toàn quyền hệ thống");
        Role hrRole = getOrCreateRole("HR", "Chuyên viên quản lý nhân sự & tuyển dụng");
        Role mentorRole = getOrCreateRole("Mentor", "Người hướng dẫn và đánh giá thực tập sinh");
        Role internRole = getOrCreateRole("Intern", "Thực tập sinh tham gia chương trình");

        // 4. Gán quyền mặc định cho các vai trò hệ thống
        assignDefaultPermissions(adminRole, hrRole, mentorRole, internRole);

        // 5. Khởi tạo tài khoản mẫu (Accounts) với mật khẩu mặc định "123456"
        createAccountIfNotExist("admin", "123456", 6, adminRole);
        createAccountIfNotExist("hr", "123456", 2, hrRole);
        createAccountIfNotExist("mentor", "123456", 3, mentorRole);
        createAccountIfNotExist("mentor_dev", "123456", 9, mentorRole);
        createAccountIfNotExist("mentor_qa", "123456", 10, mentorRole);
        createAccountIfNotExist("mentor_sec", "123456", 11, mentorRole);
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
            insertUserIfNotExist(9, "Trần Minh Quang", "quang.dev@internhub.com", "0921112233", "1991-04-15", "MALE", "Hà Nội", "https://api.dicebear.com/7.x/avataaars/svg?seed=Quang");
            insertUserIfNotExist(10, "Vũ Thị Thu Hà", "ha.qa@internhub.com", "0922223344", "1993-08-20", "FEMALE", "Hà Nội", "https://api.dicebear.com/7.x/avataaars/svg?seed=Ha");
            insertUserIfNotExist(11, "Đặng Quốc Bảo", "bao.sec@internhub.com", "0923335566", "1990-11-05", "MALE", "Hà Nội", "https://api.dicebear.com/7.x/avataaars/svg?seed=Bao");

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

    private void initializePermissionsIfNotExist() {
        if (permissionRepository == null) {
            log.debug("PermissionRepository không khả dụng, bỏ qua khởi tạo permissions.");
            return;
        }

        List<Permission> standardPermissions = List.of(
                // USER
                Permission.builder().code("USER_VIEW").name("Xem danh sách người dùng").module("USER").description("Xem danh sách và thông tin tài khoản người dùng").build(),
                Permission.builder().code("USER_MANAGE").name("Quản lý người dùng").module("USER").description("Khóa/mở khóa tài khoản và phân vai trò người dùng").build(),

                // ROLE
                Permission.builder().code("ROLE_VIEW").name("Xem danh mục vai trò").module("ROLE").description("Xem danh sách vai trò và ma trận phân quyền").build(),
                Permission.builder().code("ROLE_MANAGE").name("Quản lý vai trò & quyền").module("ROLE").description("Tạo, sửa, xóa vai trò và gán quyền cho vai trò").build(),

                // INTERN
                Permission.builder().code("INTERN_VIEW").name("Xem hồ sơ thực tập sinh").module("INTERN").description("Xem danh sách, tìm kiếm và chi tiết hồ sơ thực tập sinh").build(),
                Permission.builder().code("INTERN_CREATE").name("Tạo hồ sơ thực tập sinh").module("INTERN").description("Thêm mới hồ sơ thực tập sinh vào hệ thống").build(),
                Permission.builder().code("INTERN_EDIT").name("Chỉnh sửa hồ sơ thực tập sinh").module("INTERN").description("Cập nhật thông tin thực tập sinh").build(),
                Permission.builder().code("INTERN_APPROVE").name("Phê duyệt/Từ chối hồ sơ").module("INTERN").description("Phê duyệt hoặc từ chối hồ sơ ứng tuyển thực tập").build(),
                Permission.builder().code("INTERN_ASSIGN_MENTOR").name("Phân công mentor").module("INTERN").description("Gán người hướng dẫn phụ trách cho thực tập sinh").build(),

                // PROGRAM
                Permission.builder().code("PROGRAM_VIEW").name("Xem chương trình thực tập").module("PROGRAM").description("Xem danh sách chương trình đào tạo đang mở tuyển").build(),
                Permission.builder().code("PROGRAM_MANAGE").name("Quản lý chương trình thực tập").module("PROGRAM").description("Tạo mới, chỉnh sửa, đóng/mở chương trình thực tập").build(),

                // CONTRACT
                Permission.builder().code("CONTRACT_VIEW").name("Xem hợp đồng thực tập").module("CONTRACT").description("Xem danh sách và chi tiết hợp đồng đào tạo thực tập").build(),
                Permission.builder().code("CONTRACT_MANAGE").name("Quản lý hợp đồng thực tập").module("CONTRACT").description("Tải lên hợp đồng, cập nhật đãi ngộ và xác nhận ký").build(),

                // DOCUMENT
                Permission.builder().code("DOCUMENT_VIEW").name("Xem tài liệu & CV").module("DOCUMENT").description("Xem CV và các giấy tờ đính kèm của thực tập sinh").build(),
                Permission.builder().code("DOCUMENT_REVIEW").name("Phê duyệt tài liệu").module("DOCUMENT").description("Duyệt hoặc từ chối CV và tài liệu nộp").build(),

                // SYSTEM
                Permission.builder().code("SYSTEM_BACKUP").name("Quản trị sao lưu dữ liệu").module("SYSTEM").description("Kích hoạt sao lưu toàn diện và khôi phục dữ liệu").build(),
                Permission.builder().code("SYSTEM_AUDIT_VIEW").name("Xem nhật ký kiểm toán").module("SYSTEM").description("Xem lịch sử thao tác và vết kiểm toán hệ thống").build()
        );

        for (Permission perm : standardPermissions) {
            if (!permissionRepository.existsByCode(perm.getCode())) {
                permissionRepository.save(perm);
                log.info("-> Đã khởi tạo Permission: {} [{}]", perm.getCode(), perm.getName());
            }
        }
    }

    private void assignDefaultPermissions(Role adminRole, Role hrRole, Role mentorRole, Role internRole) {
        if (permissionRepository == null) {
            return;
        }

        List<Permission> allPermissions = permissionRepository.findAll();
        if (allPermissions.isEmpty()) {
            return;
        }

        // 1. Admin: Nhận tất cả quyền
        if (adminRole.getPermissions() == null || adminRole.getPermissions().isEmpty()) {
            adminRole.setPermissions(new HashSet<>(allPermissions));
            roleRepository.save(adminRole);
            log.info("-> Đã gán toàn bộ {} quyền cho vai trò Admin", allPermissions.size());
        }

        // 2. HR: Quyền về intern, program, contract, document và user view
        if (hrRole.getPermissions() == null || hrRole.getPermissions().isEmpty()) {
            Set<Permission> hrPerms = allPermissions.stream()
                    .filter(p -> List.of("INTERN", "PROGRAM", "CONTRACT", "DOCUMENT").contains(p.getModule()) || "USER_VIEW".equals(p.getCode()))
                    .collect(Collectors.toSet());
            hrRole.setPermissions(hrPerms);
            roleRepository.save(hrRole);
            log.info("-> Đã gán {} quyền cho vai trò HR", hrPerms.size());
        }

        // 3. Mentor: Quyền xem intern, document, program
        if (mentorRole.getPermissions() == null || mentorRole.getPermissions().isEmpty()) {
            Set<Permission> mentorPerms = allPermissions.stream()
                    .filter(p -> List.of("INTERN_VIEW", "DOCUMENT_VIEW", "PROGRAM_VIEW").contains(p.getCode()))
                    .collect(Collectors.toSet());
            mentorRole.setPermissions(mentorPerms);
            roleRepository.save(mentorRole);
            log.info("-> Đã gán {} quyền cho vai trò Mentor", mentorPerms.size());
        }

        // 4. Intern: Quyền xem intern, document, contract, program
        if (internRole.getPermissions() == null || internRole.getPermissions().isEmpty()) {
            Set<Permission> internPerms = allPermissions.stream()
                    .filter(p -> List.of("INTERN_VIEW", "DOCUMENT_VIEW", "CONTRACT_VIEW", "PROGRAM_VIEW").contains(p.getCode()))
                    .collect(Collectors.toSet());
            internRole.setPermissions(internPerms);
            roleRepository.save(internRole);
            log.info("-> Đã gán {} quyền cho vai trò Intern", internPerms.size());
        }
    }

    private Role getOrCreateRole(String roleName) {
        return getOrCreateRole(roleName, null);
    }

    private Role getOrCreateRole(String roleName, String description) {
        return roleRepository.findByName(roleName)
                .map(existing -> {
                    boolean changed = false;
                    if (existing.getIsSystem() == null || !existing.getIsSystem()) {
                        existing.setIsSystem(true);
                        changed = true;
                    }
                    if (existing.getDescription() == null && description != null) {
                        existing.setDescription(description);
                        changed = true;
                    }
                    return changed ? roleRepository.save(existing) : existing;
                })
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .name(roleName)
                            .description(description)
                            .isSystem(true)
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
