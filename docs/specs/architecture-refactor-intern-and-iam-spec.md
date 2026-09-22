# 📋 Đặc Tả Kiến Trúc: Tái Cấu Trúc Hệ Thống Thành 3 Microservices
## (`identity-and-access-service`, `intern-and-program-service`, `reporting-and-integration-service`)

> **Tài liệu tham chiếu:** [AGENTS.md](file:///c:/Users/Admin/InternHub/AGENTS.md), [.antigravity/rules.md](file:///c:/Users/Admin/InternHub/.antigravity/rules.md), [.antigravity/spec-rules.md](file:///c:/Users/Admin/InternHub/.antigravity/spec-rules.md)  
> **Phiên bản:** 2.0.0 (Cập nhật kiến trúc 3 Services theo phản hồi)  
> **Trạng thái:** DRAFT - Chờ phê duyệt (Pending Approval)

---

## 1. Feature Overview (Tổng Quan Kiến Trúc Mới)

- **Tên sáng kiến kiến trúc:** Tái cấu trúc phân rã `employee-service` thành 3 Microservices độc lập theo chuẩn Domain-Driven Design (DDD) & Single Responsibility:
  1. 🔄 **`identity-and-access-service`** (Đổi tên từ `employee-service`, Cổng nội bộ `8081`): Phụ trách Quản trị hệ thống, Quản lý danh tính (IAM), Tài khoản (`accounts`), Phân quyền vai trò (`roles`: ADMIN, HR, MENTOR, INTERN) và Xác thực tập trung (JWT Auth).
  2. 🆕 **`intern-and-program-service`** (Service mới 1, Cổng nội bộ `8082`): Phụ trách nghiệp vụ Quản lý thực tập sinh (**TM-1, TM-2, TM-3, TM-4**) và sẵn sàng mở rộng cho Quản lý chương trình thực tập (Module 3).
  3. 🆕 **`reporting-and-integration-service`** (Service mới 2, Cổng nội bộ `8083`): Phụ trách nghiệp vụ Sao lưu hệ thống (**TM-8**), Nhật ký kiểm toán & Giám sát hoạt động (**TM-9**), Báo cáo thống kê (Module 8) và Tích hợp hệ thống (Module 9).
- **Phân hệ hạ tầng:**
  - 🌐 `api-gateway` (Port `8080`): Gateway định tuyến tập trung, hỗ trợ rewrite path tương thích ngược cho Frontend cũ.
  - ⚙️ `discovery-server` (Port `8761`): Eureka Service Registry quản lý cả 3 services.
  - ⚙️ `config-server` (Port `8888`): Spring Cloud Config quản lý cấu hình tập trung cho 3 services.
  - 🗄️ `mysql-db` (Port container `3306`, host `3307`): Cơ sở dữ liệu `internhub_db`.
  - 💻 `internhub-frontend`: Kết nối trong suốt qua API Gateway.
- **Cấp độ thay đổi (Change Level):** **L4** (Kiến trúc Microservices, tách Service, Gateway Routing, Docker Orchestration, phân định ranh giới Domain).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Chuẩn hóa ranh giới nghiệp vụ (Strict Domain Boundaries):**
   - Tách biệt rõ ràng 3 năng lực cốt lõi của doanh nghiệp:
     - **IAM Domain:** Ai được vào hệ thống và có quyền gì? (`identity-and-access-service`)
     - **Core Business Domain:** Quản lý vòng đời thực tập sinh và khóa đào tạo (`intern-and-program-service`)
     - **Supporting / Operational Domain:** Giám sát, bảo vệ dữ liệu, kiểm toán và báo cáo (`reporting-and-integration-service`)
2. **Cách ly tải và sự cố (Fault & Load Isolation):**
   - Thao tác chạy `mysqldump` sao lưu dữ liệu lớn (TM-8) hoặc tổng hợp báo cáo audit log (TM-9) tiêu tốn nhiều CPU và I/O đĩa. Khi tách sang `reporting-and-integration-service`, các tác vụ này chạy độc lập mà hoàn toàn **không ảnh hưởng** đến hiệu năng đăng nhập (IAM) hay nộp hồ sơ của sinh viên (Core Service).
3. **Mở rộng linh hoạt trong tương lai:**
   - `reporting-and-integration-service` trở thành trung tâm cho toàn bộ Module 8 (Báo cáo thống kê ra Excel/PDF) và Module 9 (Tích hợp Email, Notification, Chấm công thiết bị ngoài).

---

## 3. Scope of Work (Phân Chia Chi Tiết 3 Microservices)

| Microservice | Cổng Nội Bộ | Trách Nhiệm Nghiệp Vụ & Jira Tickets | Các Bảng Dữ Liệu Quản Lý |
| :--- | :---: | :--- | :--- |
| **`identity-and-access-service`** | `8081` | - **Authentication:** Đăng nhập JWT, cấp token, refresh token.<br>- **Identity & Access Management:** Quản lý `accounts`, `roles`, phân quyền `ADMIN`, `HR`, `MENTOR`, `INTERN`.<br>- **User/Employee Management:** Quản lý thông tin tài khoản nhân viên. | `accounts`<br>`roles`<br>`account_roles` |
| **`intern-and-program-service`** | `8082` | - **TM-1:** Tạo mới hồ sơ thực tập sinh (`POST /api/interns`).<br>- **TM-2:** Cập nhật thông tin hồ sơ (`PUT /api/interns/{id}`).<br>- **TM-3:** Tìm kiếm, lọc và phân trang hồ sơ (`GET /api/interns`).<br>- **TM-4:** Quản lý tải lên CV & đơn thực tập (`POST /api/interns/{code}/documents`).<br>- **Module 3:** Chương trình thực tập (Mở rộng). | `intern_profiles`<br>`intern_documents`<br>*(tương lai: `internship_programs`)* |
| **`reporting-and-integration-service`** | `8083` | - **TM-8:** Sao lưu dữ liệu hệ thống định kỳ (Cron 02:00 AM, On-demand backup, download file `.sql.gz`, retention 30 ngày).<br>- **TM-9:** Nhật ký hoạt động & kiểm toán hệ thống (`audit_logs`, truy vấn lọc đa tiêu chí, thống kê KPI, xuất file CSV).<br>- **Module 8 & 9:** Báo cáo tổng hợp, Email/In-app thông báo. | `backup_history`<br>`audit_logs`<br>*(tương lai: `reports`, `notifications`)* |

---

## 4. Potential Logic Loopholes & Mitigations (Edge Cases & Rủi Ro)

### 4.1. Edge Case 1: Ghi nhận Audit Log giữa các Microservices (Cross-Service Auditing)
- **Vấn đề:** Khi `intern-and-program-service` thực hiện tạo hồ sơ thực tập sinh (TM-1), làm sao để ghi log vào bảng `audit_logs` khi bảng này nằm tại `reporting-and-integration-service`?
- **Giải pháp:**
  - *Giai đoạn hiện tại (Shared Database `internhub_db`):* Cả 2 service cùng kết nối tới database `internhub_db`. `intern-and-program-service` có thể tích hợp thư viện Audit AOP ghi trực tiếp vào bảng `audit_logs`, hoặc gọi REST API nội bộ `POST /api/system/audit-logs/internal` sang `reporting-and-integration-service`.
  - Để đảm bảo tính độc lập cao nhất, `reporting-and-integration-service` cung cấp endpoint nhận log bất đồng bộ, hoặc sử dụng chung bảng `audit_logs` trong MySQL.

### 4.2. Edge Case 2: Quyền thực thi lệnh `mysqldump` trong Container Backup
- **Vấn đề:** TM-8 yêu cầu công cụ `mysqldump` để sao lưu. Container `reporting-and-integration-service` phải có cài đặt `default-mysql-client` hoặc image hỗ trợ CLI MySQL.
- **Giải pháp:** Cập nhật Dockerfile của `reporting-and-integration-service` để cài đặt `mysql-client` (hoặc mount CLI tools) và mount volume `./data/backups:/backups`.

### 4.3. Edge Case 3: Xung đột cổng Port khi chạy đồng thời 3 Services
- **Vấn đề:** Nếu cấu hình trùng cổng sẽ gây crash ứng dụng.
- **Giải pháp:**
  - `identity-and-access-service`: Cổng `8081`
  - `intern-and-program-service`: Cổng `8082`
  - `reporting-and-integration-service`: Cổng `8083`
  - Tất cả giao tiếp bên ngoài đi qua `api-gateway` (Port `8080`).

### 4.4. Edge Case 4: Bảo mật & Tương thích ngược Gateway Routes
- **Vấn đề:** Frontend hiện tại đang gọi các URL:
  - `/api/auth/**` -> IAM
  - `/api/employees/interns/**` -> Intern
  - `/api/system/backups/**` -> Backup
  - `/api/system/audit-logs/**` -> Audit Log
- **Giải pháp:** Cấu hình `api-gateway.yml` định tuyến rõ ràng:
  - `/api/auth/**`, `/api/users/**`, `/api/employees/**` -> `identity-and-access-service`
  - `/api/interns/**` -> `intern-and-program-service`
  - `/api/employees/interns/**` -> Rewrite sang `/api/interns/**` trên `intern-and-program-service`
  - `/api/system/backups/**`, `/api/system/audit-logs/**`, `/api/system/reports/**` -> `reporting-and-integration-service`

### 4.5. Edge Case 5: Đồng bộ JWT Token Verification trên cả 3 Services
- **Vấn đề:** Người dùng login tại `identity-and-access-service`, sau đó gửi Bearer Token sang `intern-and-program-service` hoặc `reporting-and-integration-service`. Nếu lệch Secret Key sẽ bị lỗi 401.
- **Giải pháp:** Đặt biến môi trường bí mật `JWT_SECRET` tập trung tại `config-server` (trong `application.yml` dùng chung), đảm bảo cả 3 microservices đều giải mã token với cùng 1 secret key.

---

## 5. API Gateway Routing Matrix (`api-gateway.yml`)

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      server:
        webflux:
          discovery:
            locator:
              enabled: true
              lower-case-service-id: true
          routes:
            # 1. Identity and Access Service (Auth, Account, IAM)
            - id: identity-auth
              uri: lb://identity-and-access-service
              predicates:
                - Path=/api/auth/**

            - id: identity-users
              uri: lb://identity-and-access-service
              predicates:
                - Path=/api/users/**

            - id: identity-employees
              uri: lb://identity-and-access-service
              predicates:
                - Path=/api/employees
                - "!Path=/api/employees/interns/**"

            # 2. Intern and Program Service (Interns, Documents, Programs)
            - id: intern-and-program-service
              uri: lb://intern-and-program-service
              predicates:
                - Path=/api/interns/**

            # 2.1 Backward Compatibility cho Frontend cũ
            - id: intern-legacy-backward-compat
              uri: lb://intern-and-program-service
              predicates:
                - Path=/api/employees/interns/**
              filters:
                - RewritePath=/api/employees/interns(?<segment>/?.*), /api/interns$\{segment}

            # 3. Reporting and Integration Service (Backup TM-8, Audit Log TM-9)
            - id: reporting-backup
              uri: lb://reporting-and-integration-service
              predicates:
                - Path=/api/system/backups/**

            - id: reporting-audit
              uri: lb://reporting-and-integration-service
              predicates:
                - Path=/api/system/audit-logs/**
```

---

## 6. Docker Compose Orchestration Matrix (`docker-compose.yml`)

```yaml
services:
  mysql-db:
    # Port: 3307:3306, internhub_db

  config-server:
    # Port: 8888:8888

  discovery-server:
    # Port: 8761:8761

  api-gateway:
    # Port: 8080:8080

  # Microservice 1: IAM & System Administration
  identity-and-access-service:
    build: ./identity-and-access-service
    container_name: identity-and-access-service
    restart: unless-stopped
    ports:
      - "8081:8081"
    depends_on:
      mysql-db:
        condition: service_healthy
      config-server:
        condition: service_healthy
      discovery-server:
        condition: service_healthy

  # Microservice 2: Intern Profile & Program Management
  intern-and-program-service:
    build: ./intern-and-program-service
    container_name: intern-and-program-service
    restart: unless-stopped
    ports:
      - "8082:8082"
    volumes:
      - ./data/uploads:/uploads
    depends_on:
      mysql-db:
        condition: service_healthy
      config-server:
        condition: service_healthy
      discovery-server:
        condition: service_healthy

  # Microservice 3: Reporting, System Backup & Audit Trail
  reporting-and-integration-service:
    build: ./reporting-and-integration-service
    container_name: reporting-and-integration-service
    restart: unless-stopped
    ports:
      - "8083:8083"
    volumes:
      - ./data/backups:/backups
    depends_on:
      mysql-db:
        condition: service_healthy
      config-server:
        condition: service_healthy
      discovery-server:
        condition: service_healthy
```

---

## 7. Acceptance Criteria Checklist (Tiêu Chí Nghiệm Thu)

- [ ] **AC-1:** Gradle Root nhận diện chính xác cả 3 sub-projects: `identity-and-access-service`, `intern-and-program-service`, `reporting-and-integration-service`.
- [ ] **AC-2:** Chạy lệnh `.\gradlew compileJava` tại root thành công 100% cho cả 3 services.
- [ ] **AC-3:** Cả 3 services đăng ký thành công lên Eureka Discovery Dashboard (`http://localhost:8761`) với trạng thái `UP`.
- [ ] **AC-4:** Đăng nhập `/api/auth/login` tại `identity-and-access-service` trả về JWT Token hợp lệ.
- [ ] **AC-5:** Sử dụng Token gọi API `/api/interns` tại `intern-and-program-service` qua Gateway hoạt động hoàn hảo (TM-1, TM-2, TM-3, TM-4).
- [ ] **AC-6:** Sử dụng Token gọi API `/api/system/backups` và `/api/system/audit-logs` tại `reporting-and-integration-service` qua Gateway hoạt động hoàn hảo (TM-8, TM-9).
- [ ] **AC-7:** Frontend gọi đường dẫn cũ `/api/employees/interns` vẫn hoạt động bình thường nhờ Gateway RewritePath.
- [ ] **AC-8:** Toàn bộ hệ thống container trong Docker Compose khởi động ổn định và đạt trạng thái healthy.
