package org.example.employeeservice.system.audit.entity;

public enum AuditModule {
    AUTH,       // Xác thực, phân quyền, đăng nhập
    INTERN,     // Quản lý hồ sơ thực tập sinh
    DOCUMENT,   // Quản lý hồ sơ, tài liệu, CV
    SYSTEM,     // Sao lưu hệ thống, cấu hình nền tảng
    USER        // Quản lý người dùng, phân quyền
}
