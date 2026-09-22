# ✅ Danh Sách Nhiệm Vụ Triển Khai (Tasks Checklist)
## Phân Tách 3 Microservices: IAM, Intern & Program, Reporting & Integration

> **Hướng dẫn:** Mỗi task được thiết kế độc lập, có thể kiểm tra và review bằng diff.  
> Tích `[x]` sau khi hoàn tất và đối chiếu bằng chứng kiểm thử.

---

## 1. Hạ Tầng & Cấu Hình Tập Trung
- [ ] **TASK-01:** Cập nhật `settings.gradle` tại root: thêm `identity-and-access-service`, `intern-and-program-service`, `reporting-and-integration-service`.
- [ ] **TASK-02:** Tạo `config-repo-local/identity-and-access-service.yml` (port 8081).
- [ ] **TASK-03:** Tạo `config-repo-local/intern-and-program-service.yml` (port 8082, multipart upload, upload-dir).
- [ ] **TASK-04:** Tạo `config-repo-local/reporting-and-integration-service.yml` (port 8083, storage-dir `/backups`, cron).
- [ ] **TASK-05:** Cập nhật `config-repo-local/api-gateway.yml` định tuyến cả 3 services kèm RewritePath tương thích ngược.

---

## 2. Tái Cấu Trúc `employee-service` -> `identity-and-access-service` (Port 8081)
- [ ] **TASK-06:** Đổi tên thư mục `employee-service` thành `identity-and-access-service`.
- [ ] **TASK-07:** Cập nhật `build.gradle` (description, artifact name).
- [ ] **TASK-08:** Cập nhật `src/main/resources/application.yml` (`spring.application.name: identity-and-access-service`).
- [ ] **TASK-09:** Dọn dẹp package `intern/` (chuyển sang service intern).
- [ ] **TASK-10:** Dọn dẹp package `system/` (chuyển sang service reporting).
- [ ] **TASK-11:** Kiểm tra biên dịch: `.\gradlew :identity-and-access-service:compileJava`.

---

## 3. Khởi Tạo `intern-and-program-service` (Port 8082 - TM-1, TM-2, TM-3, TM-4)
- [ ] **TASK-12:** Khởi tạo thư mục và `build.gradle` cho `intern-and-program-service`.
- [ ] **TASK-13:** Tạo `Dockerfile` và `src/main/resources/application.yml`.
- [ ] **TASK-14:** Tạo Main Class: `InternAndProgramServiceApplication.java`.
- [ ] **TASK-15:** Thiết lập package `common/`: `BaseEntity`, `ApiResponse`, `PageResponse`, Global Exceptions.
- [ ] **TASK-16:** Thiết lập package `security/`: `JwtAuthenticationFilter`, `JwtTokenProvider`, `SecurityConfig` stateless.
- [ ] **TASK-17:** Thiết lập package `intern/`: Entities (`InternProfile`, `InternDocument`), Repositories, Services, DTOs.
- [ ] **TASK-18:** Cập nhật Controllers: `InternProfileController` (mapping `/api/interns`), `InternDocumentController`.
- [ ] **TASK-19:** Kiểm tra biên dịch: `.\gradlew :intern-and-program-service:compileJava`.

---

## 4. Khởi Tạo `reporting-and-integration-service` (Port 8083 - TM-8, TM-9)
- [ ] **TASK-20:** Khởi tạo thư mục và `build.gradle` cho `reporting-and-integration-service`.
- [ ] **TASK-21:** Tạo `Dockerfile` và `src/main/resources/application.yml`.
- [ ] **TASK-22:** Tạo Main Class: `ReportingAndIntegrationServiceApplication.java`.
- [ ] **TASK-23:** Thiết lập package `common/`: `BaseEntity`, `ApiResponse`, `PageResponse`, Global Exceptions.
- [ ] **TASK-24:** Thiết lập package `security/`: `JwtAuthenticationFilter`, `JwtTokenProvider`, `SecurityConfig` stateless.
- [ ] **TASK-25:** Di chuyển module TM-8 (Sao lưu hệ thống: `BackupHistory`, `BackupService`, `BackupController`).
- [ ] **TASK-26:** Di chuyển module TM-9 (Nhật ký kiểm toán: `AuditLog`, `AuditLogAspect`, `AuditLogService`, `AuditLogController`).
- [ ] **TASK-27:** Kiểm tra biên dịch: `.\gradlew :reporting-and-integration-service:compileJava`.

---

## 5. Cập Nhật Điều Phối Docker Compose & Scripts
- [ ] **TASK-28:** Cập nhật `docker-compose.yml` định nghĩa 3 containers:
  - `identity-and-access-service` (Port 8081)
  - `intern-and-program-service` (Port 8082, volume `./data/uploads:/uploads`)
  - `reporting-and-integration-service` (Port 8083, volume `./data/backups:/backups`)
- [ ] **TASK-29:** Cập nhật `build-all.bat` và `build-all.sh`.

---

## 6. Kiểm Thử Hệ Thống & Đối Chiếu (Verification & Converge)
- [ ] **TASK-30:** Biên dịch toàn bộ hệ sinh thái: `.\gradlew compileJava`.
- [ ] **TASK-31:** Đóng gói tất cả JARs: `.\gradlew bootJar -x test`.
- [ ] **TASK-32:** Kiểm thử API Gateway (Port 8080):
  - `POST /api/auth/login` (200 OK)
  - `GET /api/interns` (200 OK)
  - `POST /api/interns` (201 Created)
  - `GET /api/employees/interns` (200 OK qua Gateway RewritePath)
  - `GET /api/system/backups` (200 OK)
  - `GET /api/system/audit-logs` (200 OK)
- [ ] **TASK-33:** Kiểm thử giao diện `internhub-frontend`.
