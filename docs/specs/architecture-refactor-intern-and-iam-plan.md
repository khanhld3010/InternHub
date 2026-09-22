# 🗺️ Kế Hoạch Triển Khai Kỹ Thuật (Architecture Refactoring Plan)
## Mô hình 3 Microservices: IAM, Intern & Program, Reporting & Integration

> **Mục tiêu:** Thực hiện phân tách microservices, đổi tên `employee-service` thành `identity-and-access-service` (Port 8081), khởi tạo `intern-and-program-service` (Port 8082 - TM-1, TM-2, TM-3, TM-4) và khởi tạo `reporting-and-integration-service` (Port 8083 - TM-8, TM-9).  
> **Thay đổi cấp độ:** **L4** (Kiến trúc, Hạ tầng Docker, Phân tách Domain).

---

## 1. Rà Soát Hiện Trạng & Bản Đồ Phân Tách Code

| Phân Vùng Mã Nguồn Hiện Tại Trong `employee-service` | Chuyển Về Service Đích Mới | Trách Nhiệm Nghiệp Vụ |
| :--- | :--- | :--- |
| `security/`, `entity/Account`, `entity/Role`, `AuthController`, `EmployeeController` | **`identity-and-access-service`** (Port 8081) | Xác thực JWT, Quản lý tài khoản, Vai trò (ADMIN/HR/MENTOR/INTERN), Nhân viên |
| `intern/` (`InternProfile`, `InternDocument`, DTOs, Repositories, Services, Controllers) | **`intern-and-program-service`** (Port 8082) | TM-1 (Tạo hồ sơ), TM-2 (Cập nhật), TM-3 (Tìm kiếm/Lọc), TM-4 (Upload CV/Đơn) |
| `system/entity/BackupHistory`, `system/service/Backup*`, `system/controller/Backup*` | **`reporting-and-integration-service`** (Port 8083) | TM-8 (Sao lưu DB MySQL định kỳ Cron 02:00 AM & On-demand, tải file backup) |
| `system/audit/` (`AuditLog`, Aspect, Events, Service, Controller, Export CSV) | **`reporting-and-integration-service`** (Port 8083) | TM-9 (Nhật ký kiểm toán hệ thống, bộ lọc, xem chi tiết, thống kê, export CSV) |
| `common/` (`BaseEntity`, `ApiResponse`, `PageResponse`, Global Exceptions) | **Cả 3 Services** (Dùng chung chuẩn) | Chuẩn hóa Entity & Response toàn hệ sinh thái |

---

## 2. Chiến Lược Thực Thi Từng Bước (Phase-by-Phase Roadmap)

### Phase 1: Chuẩn bị Cấu hình Tập trung & Root Project
1. **`settings.gradle` tại Root:**
   ```groovy
   rootProject.name = 'internhub'
   include 'config-server'
   include 'discovery-server'
   include 'api-gateway'
   include 'identity-and-access-service'
   include 'intern-and-program-service'
   include 'reporting-and-integration-service'
   ```
2. **Cấu hình tập trung tại `config-repo-local/`:**
   - Tạo `identity-and-access-service.yml` (Port 8081, cấu hình DB, JWT).
   - Tạo `intern-and-program-service.yml` (Port 8082, cấu hình DB, JWT, multipart upload 10MB, volume upload).
   - Tạo `reporting-and-integration-service.yml` (Port 8083, cấu hình DB, JWT, Cron backup, storage dir `/backups`).
   - Cập nhật `api-gateway.yml` với ma trận routing cho cả 3 services và bộ lọc RewritePath tương thích ngược.

### Phase 2: Đổi tên và Tối ưu `employee-service` -> `identity-and-access-service`
1. Đổi tên thư mục `employee-service/` thành `identity-and-access-service/`.
2. Sửa `build.gradle`: `description = 'identity-and-access-service'`.
3. Sửa `src/main/resources/application.yml`: `spring.application.name: identity-and-access-service`.
4. Dọn dẹp:
   - Xóa bỏ package `intern/` (chuyển giao sang `intern-and-program-service`).
   - Xóa bỏ package `system/` (chuyển giao sang `reporting-and-integration-service`).
5. Giữ lại: `common/`, `security/`, `entity/` (Account, Role), `repository/`, `service/`, `controller/` (Auth, Employee).
6. Kiểm tra biên dịch độc lập: `.\gradlew :identity-and-access-service:compileJava`.

### Phase 3: Khởi tạo Service 1: `intern-and-program-service`
1. Tạo thư mục `intern-and-program-service/`.
2. Tạo `build.gradle` với các dependency: Web, JPA, Security, Validation, Eureka Client, JJWT, MySQL.
3. Tạo `Dockerfile` và `src/main/resources/application.yml` (kết nối Config Server, port 8082).
4. Tạo Main Class: `InternAndProgramServiceApplication.java`.
5. Thiết lập package `common/` (`BaseEntity`, `ApiResponse`, `PageResponse`, Global Exceptions).
6. Thiết lập package `security/` (`JwtAuthenticationFilter`, `JwtTokenProvider`, `SecurityConfig` stateless).
7. Di chuyển trọn vẹn package `intern/` (TM-1, TM-2, TM-3, TM-4) sang service này.
8. Cập nhật controller endpoint chuẩn: `@RequestMapping("/api/interns")`.
9. Kiểm tra biên dịch độc lập: `.\gradlew :intern-and-program-service:compileJava`.

### Phase 4: Khởi tạo Service 2: `reporting-and-integration-service`
1. Tạo thư mục `reporting-and-integration-service/`.
2. Tạo `build.gradle` với các dependency: Web, JPA, Security, Validation, Eureka Client, JJWT, MySQL.
3. Tạo `Dockerfile` (hỗ trợ volume `/backups`).
4. Tạo `src/main/resources/application.yml` (kết nối Config Server, port 8083).
5. Tạo Main Class: `ReportingAndIntegrationServiceApplication.java`.
6. Thiết lập package `common/` (`BaseEntity`, `ApiResponse`, `PageResponse`, Global Exceptions).
7. Thiết lập package `security/` (`JwtAuthenticationFilter`, `JwtTokenProvider`, `SecurityConfig` stateless).
8. Di chuyển module TM-8 (Sao lưu hệ thống: `BackupHistory`, `BackupService`, `BackupController`).
9. Di chuyển module TM-9 (Nhật ký kiểm toán: `AuditLog`, `AuditLogAspect`, `AuditLogService`, `AuditLogController`).
10. Kiểm tra biên dịch độc lập: `.\gradlew :reporting-and-integration-service:compileJava`.

### Phase 5: Cập nhật Docker Compose & Script Đóng gói
1. Sửa `docker-compose.yml`:
   - Thay thế service `employee-service` bằng 3 containers: `identity-and-access-service`, `intern-and-program-service`, `reporting-and-integration-service`.
   - Mount Volume cho file upload `./data/uploads:/uploads` gắn vào `intern-and-program-service`.
   - Mount Volume cho file backup `./data/backups:/backups` gắn vào `reporting-and-integration-service`.
2. Cập nhật script `build-all.bat` và `build-all.sh`.

### Phase 6: Kiểm Thử Toàn Diện & Tương Thích
1. Chạy `.\gradlew compileJava` tại root (yêu cầu cả 6 subprojects đều thành công).
2. Kiểm tra đóng gói JAR: `.\gradlew bootJar -x test`.
3. Kiểm tra các luồng API:
   - `POST /api/auth/login` (200 OK từ IAM Service)
   - `GET /api/interns` (200 OK từ Intern Service)
   - `POST /api/employees/interns` (200/201 OK qua Gateway RewritePath)
   - `GET /api/system/backups` (200 OK từ Reporting Service)
   - `GET /api/system/audit-logs` (200 OK từ Reporting Service)
