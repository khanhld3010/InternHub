package org.example.employeeservice.config;

import org.example.employeeservice.entity.Account;
import org.example.employeeservice.entity.Role;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DataInitializerTest {

    @Test
    @DisplayName("Khởi tạo vai trò và tài khoản chuẩn DB (Admin, HR, Mentor, Intern) khi database trống, đảm bảo tính bất biến (idempotency)")
    void testRun_WhenDbEmpty_ShouldInitializeRolesAndAccounts() {
        Map<String, Role> roleStore = new HashMap<>();
        List<Account> savedAccounts = new ArrayList<>();

        RoleRepository roleRepository = createMockRoleRepository(roleStore);
        AccountRepository accountRepository = createMockAccountRepository(savedAccounts);
        PasswordEncoder passwordEncoder = createMockPasswordEncoder();

        DataInitializer initializer = new DataInitializer(roleRepository, accountRepository, passwordEncoder);

        // Lần chạy 1: Database trống -> Khởi tạo đầy đủ 4 roles và 4 accounts
        initializer.run();

        assertEquals(4, roleStore.size(), "Cần khởi tạo đủ 4 vai trò chuẩn DB: Admin, HR, Mentor, Intern");
        assertTrue(roleStore.containsKey("Admin"));
        assertTrue(roleStore.containsKey("HR"));
        assertTrue(roleStore.containsKey("Mentor"));
        assertTrue(roleStore.containsKey("Intern"));

        assertEquals(4, savedAccounts.size(), "Cần khởi tạo đủ 4 tài khoản mẫu");
        assertTrue(savedAccounts.stream().anyMatch(a -> "admin".equals(a.getUsername()) && "Admin".equals(a.getRole().getName()) && Integer.valueOf(6).equals(a.getUserId())));
        assertTrue(savedAccounts.stream().anyMatch(a -> "hr".equals(a.getUsername()) && "HR".equals(a.getRole().getName()) && Integer.valueOf(2).equals(a.getUserId())));
        assertTrue(savedAccounts.stream().anyMatch(a -> "mentor".equals(a.getUsername()) && "Mentor".equals(a.getRole().getName()) && Integer.valueOf(3).equals(a.getUserId())));
        assertTrue(savedAccounts.stream().anyMatch(a -> "intern".equals(a.getUsername()) && "Intern".equals(a.getRole().getName()) && Integer.valueOf(4).equals(a.getUserId())));

        // Mật khẩu phải được mã hóa và trạng thái ACTIVE
        for (Account account : savedAccounts) {
            assertEquals("ACTIVE", account.getStatus());
            assertTrue(passwordEncoder.matches("123456", account.getPasswordHash()));
        }

        // Lần chạy 2: Chạy lại khi dữ liệu đã có -> Không được tạo trùng lặp
        initializer.run();
        assertEquals(4, roleStore.size(), "Không tạo trùng lặp role khi chạy lại");
        assertEquals(4, savedAccounts.size(), "Không tạo trùng lặp account khi chạy lại");
    }

    @Test
    @DisplayName("Khởi tạo an toàn khi đã có sẵn 4 roles và tài khoản admin trong DB thực tế")
    void testRun_WhenExistingRolesAndAdminPresent_ShouldOnlySeedMissingAccounts() {
        Map<String, Role> roleStore = new HashMap<>();
        Role adminRole = Role.builder().id(1).name("Admin").build();
        Role hrRole = Role.builder().id(2).name("HR").build();
        Role mentorRole = Role.builder().id(3).name("Mentor").build();
        Role internRole = Role.builder().id(4).name("Intern").build();
        roleStore.put("Admin", adminRole);
        roleStore.put("HR", hrRole);
        roleStore.put("Mentor", mentorRole);
        roleStore.put("Intern", internRole);

        List<Account> savedAccounts = new ArrayList<>();
        Account existingAdmin = Account.builder()
                .id(1)
                .userId(6)
                .username("admin")
                .role(adminRole)
                .passwordHash("encoded_123456")
                .status("ACTIVE")
                .build();
        savedAccounts.add(existingAdmin);

        RoleRepository roleRepository = createMockRoleRepository(roleStore);
        AccountRepository accountRepository = createMockAccountRepository(savedAccounts);
        PasswordEncoder passwordEncoder = createMockPasswordEncoder();

        DataInitializer initializer = new DataInitializer(roleRepository, accountRepository, passwordEncoder);

        initializer.run();

        assertEquals(4, roleStore.size(), "Không sinh thêm role mới ngoài 4 role ban đầu");
        assertEquals(4, savedAccounts.size(), "Bổ sung đủ 3 tài khoản còn thiếu (hr, mentor, intern) và giữ nguyên admin");
        assertTrue(savedAccounts.stream().anyMatch(a -> "hr".equals(a.getUsername())));
        assertTrue(savedAccounts.stream().anyMatch(a -> "mentor".equals(a.getUsername())));
        assertTrue(savedAccounts.stream().anyMatch(a -> "intern".equals(a.getUsername())));
    }

    @Test
    @DisplayName("Khởi tạo bảng users và dữ liệu người dùng mẫu khi jdbcTemplate khả dụng")
    void testRun_WithJdbcTemplate_ShouldExecuteUserSchemaAndInserts() {
        RoleRepository roleRepository = createMockRoleRepository(new HashMap<>());
        AccountRepository accountRepository = createMockAccountRepository(new ArrayList<>());
        PasswordEncoder passwordEncoder = createMockPasswordEncoder();
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);

        org.mockito.Mockito.when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(0);

        DataInitializer initializer = new DataInitializer(roleRepository, accountRepository, passwordEncoder, jdbcTemplate);
        initializer.run();

        org.mockito.Mockito.verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS users"));
        org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.atLeast(6)).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO users"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private RoleRepository createMockRoleRepository(Map<String, Role> roleStore) {
        return (RoleRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{RoleRepository.class},
                (proxy, method, args) -> {
                    if ("findByName".equals(method.getName())) {
                        String name = (String) args[0];
                        return Optional.ofNullable(roleStore.get(name));
                    }
                    if ("save".equals(method.getName())) {
                        Role role = (Role) args[0];
                        roleStore.put(role.getName(), role);
                        return role;
                    }
                    return null;
                }
        );
    }

    private AccountRepository createMockAccountRepository(List<Account> savedAccounts) {
        return (AccountRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{AccountRepository.class},
                (proxy, method, args) -> {
                    if ("existsByUsername".equals(method.getName())) {
                        String username = (String) args[0];
                        return savedAccounts.stream().anyMatch(a -> username.equals(a.getUsername()));
                    }
                    if ("existsByUserId".equals(method.getName())) {
                        Integer userId = (Integer) args[0];
                        return savedAccounts.stream().anyMatch(a -> userId.equals(a.getUserId()));
                    }
                    if ("save".equals(method.getName())) {
                        Account account = (Account) args[0];
                        savedAccounts.add(account);
                        return account;
                    }
                    return null;
                }
        );
    }

    private PasswordEncoder createMockPasswordEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "encoded_" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return ("encoded_" + rawPassword).equals(encodedPassword);
            }
        };
    }
}
