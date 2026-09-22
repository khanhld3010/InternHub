package org.example.employeeservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void testPasswordMatches() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$10$3fBMjPW33X3Lxgy53CwFV.LMrxKkDbjRUSnqQiwomoPGvQSd1W9Bi";
        assertTrue(encoder.matches("123456", hash));
    }
}
