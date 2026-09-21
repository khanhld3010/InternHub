package org.example.employeeservice.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.employeeservice.common.dto.response.ApiResponse;
import org.example.employeeservice.entity.Account;
import org.example.employeeservice.exception.ResourceNotFoundException;
import org.example.employeeservice.repository.AccountRepository;
import org.example.employeeservice.system.audit.annotation.Auditable;
import org.example.employeeservice.system.audit.entity.AuditAction;
import org.example.employeeservice.system.audit.entity.AuditModule;
import org.example.employeeservice.system.dto.response.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/api/system/users", "/api/users"})
@RequiredArgsConstructor
@Tag(name = "System User Controller", description = "Quản trị danh sách người dùng và trạng thái tài khoản")
@PreAuthorize("hasRole('ADMIN')")
public class SystemUserController {

    private final AccountRepository accountRepository;

    @Operation(summary = "Lấy toàn bộ danh sách tài khoản người dùng")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        log.info("API: Truy vấn danh sách người dùng hệ thống");
        List<Account> accounts = accountRepository.findAll();
        List<UserResponse> result = new ArrayList<>();

        for (Account acc : accounts) {
            String roleName = acc.getRole() != null ? acc.getRole().getName().replace("ROLE_", "") : "USER";
            result.add(UserResponse.builder()
                    .id(acc.getId())
                    .fullName(getUserFullName(acc.getUsername(), roleName))
                    .email(acc.getUsername().contains("@") ? acc.getUsername() : acc.getUsername() + "@internhub.vn")
                    .phone("090" + String.format("%07d", acc.getId()))
                    .department(getUserDepartment(roleName))
                    .position(getUserPosition(roleName))
                    .status(acc.getStatus())
                    .role(roleName)
                    .createdAt(acc.getCreatedAt() != null ? acc.getCreatedAt() : LocalDateTime.now())
                    .build());
        }

        return ResponseEntity.ok(ApiResponse.success(200, "Lấy danh sách người dùng thành công", result));
    }

    @Operation(summary = "Lấy thông tin người dùng theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable("id") Integer id) {
        Account acc = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));

        String roleName = acc.getRole() != null ? acc.getRole().getName().replace("ROLE_", "") : "USER";
        UserResponse response = UserResponse.builder()
                .id(acc.getId())
                .fullName(getUserFullName(acc.getUsername(), roleName))
                .email(acc.getUsername().contains("@") ? acc.getUsername() : acc.getUsername() + "@internhub.vn")
                .phone("090" + String.format("%07d", acc.getId()))
                .department(getUserDepartment(roleName))
                .position(getUserPosition(roleName))
                .status(acc.getStatus())
                .role(roleName)
                .createdAt(acc.getCreatedAt() != null ? acc.getCreatedAt() : LocalDateTime.now())
                .build();

        return ResponseEntity.ok(ApiResponse.success(200, "Lấy thông tin người dùng thành công", response));
    }

    @Operation(summary = "Khóa hoặc mở khóa tài khoản người dùng")
    @Auditable(action = AuditAction.TOGGLE_USER_STATUS, module = AuditModule.USER, description = "Thay đổi trạng thái tài khoản người dùng")
    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<ApiResponse<UserResponse>> toggleUserStatus(@PathVariable("id") Integer id) {
        log.info("API: Thay đổi trạng thái tài khoản ID={}", id);
        Account acc = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));

        String newStatus = "ACTIVE".equalsIgnoreCase(acc.getStatus()) ? "INACTIVE" : "ACTIVE";
        acc.setStatus(newStatus);
        Account updated = accountRepository.save(acc);

        String roleName = updated.getRole() != null ? updated.getRole().getName().replace("ROLE_", "") : "USER";
        UserResponse response = UserResponse.builder()
                .id(updated.getId())
                .fullName(getUserFullName(updated.getUsername(), roleName))
                .email(updated.getUsername().contains("@") ? updated.getUsername() : updated.getUsername() + "@internhub.vn")
                .phone("090" + String.format("%07d", updated.getId()))
                .department(getUserDepartment(roleName))
                .position(getUserPosition(roleName))
                .status(updated.getStatus())
                .role(roleName)
                .createdAt(updated.getCreatedAt() != null ? updated.getCreatedAt() : LocalDateTime.now())
                .build();

        return ResponseEntity.ok(ApiResponse.success(200, "Cập nhật trạng thái tài khoản thành công", response));
    }

    private String getUserFullName(String username, String role) {
        switch (role.toUpperCase()) {
            case "ADMIN":
                return "Nguyễn Văn Quản Trị (Admin)";
            case "HR":
                return "Trần Thị Tuyển Dụng (HR)";
            case "MENTOR":
                return "Lê Văn Hướng Dẫn (Mentor)";
            case "INTERN":
                return "Hoàng Kim Thực Tập (Intern)";
            default:
                return username;
        }
    }

    private String getUserDepartment(String role) {
        switch (role.toUpperCase()) {
            case "ADMIN":
                return "Ban Giám Đốc / IT Security";
            case "HR":
                return "Phòng Nhân Sự (HRD)";
            case "MENTOR":
                return "Trung Tâm Công Nghệ Phần Mềm";
            case "INTERN":
                return "Đội Dự Án AI & Microservices";
            default:
                return "Phòng Vận Hành";
        }
    }

    private String getUserPosition(String role) {
        switch (role.toUpperCase()) {
            case "ADMIN":
                return "System Administrator";
            case "HR":
                return "HR Manager & Recruiter";
            case "MENTOR":
                return "Technical Lead & Mentor";
            case "INTERN":
                return "Software Engineering Intern";
            default:
                return "Chuyên Viên";
        }
    }
}
