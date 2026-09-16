# Backend AI Agent Guidelines (InternHub Backend)

Tập tin này định nghĩa quy tắc hoạt động, thứ tự nạp ngữ cảnh và quy chuẩn phát triển dành cho AI Agent khi làm việc trong dự án **InternHub (Backend Microservices)**.

---

## 1. Context Loading Order (Thứ tự nạp Ngữ cảnh)

Trước khi thực hiện bất kỳ nhiệm vụ nào (phát triển tính năng, sửa bug, refactor), AI **BẮT BUỘC** phải nạp và tuân thủ ngữ cảnh theo thứ tự sau:

- 📖 **Tổng quan dự án & Nghiệp vụ:** Đọc [`README.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub/README.md) để nắm bức tranh tổng thể dự án.
- 🔴 **Quy chuẩn kỹ thuật BẮT BUỘC:** Đọc [`.antigravity/rules.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub/.antigravity/rules.md) chứa toàn bộ Coding Conventions, Naming Standards và Architecture Patterns.
- 🐳 **Môi trường & Vận hành:** Tham khảo [`tutorial.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub/tutorial.md) và [`docker-compose.yml`](file:///c:/Users/Luong%20Anh%20Huy/InternHub/docker-compose.yml) khi cần làm việc với Docker/MySQL và các port Microservices.

---

## 2. Tech Stack & Môi trường Phát triển

Chi tiết danh sách Tech Stack và thư viện được quản lý tập trung tại [`README.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub/README.md) và [`.antigravity/rules.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub/.antigravity/rules.md).

### Lưu ý nhanh về Môi trường (Operational Snapshot):
- **Build Tool:** Gradle Wrapper (`.\gradlew` trên Windows / `./gradlew` trên Linux/macOS)
- **Runtime:** Java 17 / Java 21, Spring Boot 3.x / 4.x
- **Microservices Infrastructure:**
  - API Gateway: Port `8080`
  - Eureka Discovery Server: Port `8761`
  - Config Server: Port `8888`
  - Employee Service: Port nội bộ `8081`
- **Database:** MySQL 8.0 (Host Port: `3307`, Container Port: `3306`, Database: `internhub_db`)

---

## 3. Skill Trigger Rules (Tự Động Kích Hoạt Skill)

AI cần tự động áp dụng các skill sau theo đúng loại tác vụ:

- **Khi thiết kế, tạo mới hoặc refactor REST API:** ➔ Sử dụng skill `api-design-principles`
- **Khi thảo luận, làm rõ ý tưởng, kiến trúc hoặc nghiệp vụ mới trước khi code:** ➔ Sử dụng skill `brainstorming`
- **Khi Refactor, tối ưu hóa code, hoặc sửa code chưa sạch:** ➔ Sử dụng skill `codebase-cleanup-refactor-clean`
- **Khi đánh giá, review code hoặc kiểm tra chất lượng Pull Request:** ➔ Sử dụng skill `code-reviewer`
- **Khi làm việc với Docker, Dockerfile, Docker Compose:** ➔ Sử dụng skill `docker-expert`
- **Khi làm việc với Java 17/21, Spring Boot hoặc các tính năng Java hiện đại:** ➔ Sử dụng skill `java-pro`
- **Khi tạo/sửa JPA Entity, Repository, Query JPQL/SQL:** ➔ Sử dụng skill `spring-data-jpa`
- **Khi áp dụng chuẩn kiến trúc, thiết kế hệ thống theo phong cách Uncle Bob (Clean Architecture, SOLID):** ➔ Sử dụng skill `uncle-bob-craft`
- **Khi viết Unit Test / Integration Test:** ➔ Sử dụng skill `unit-testing-test-generate` hoặc `java-pro`

---

## 4. Nguyên Tắc Kiến Trúc & Coding Standards

### Kiến trúc phân tầng (Package by Feature):
- `controller`: Chỉ nhận HTTP Request, validate DTO bằng `@Valid`, gọi Service, trả về `ResponseEntity<ApiResponse<T>>` hoặc `ResponseEntity<T>`. **Tuyệt đối không viết logic tại Controller**.
- `service` / `service/impl`: Chứa toàn bộ Business Logic. Sử dụng `@Transactional` cho các hàm tác động dữ liệu.
- `repository`: Interfaces kế thừa `JpaRepository` / `JpaSpecificationExecutor`.
- `dto`: Phân tách `dto/request/` và `dto/response/`. **Không trả về JPA Entity trực tiếp ra API Response**.
- `exception`: Bắt ngoại lệ tập trung qua `GlobalExceptionHandler`.
- **Dependency Injection:** Sử dụng Constructor Injection thông qua `@RequiredArgsConstructor` từ Lombok (**KHÔNG dùng `@Autowired` ở trường**).
- **Entities:** Tất cả JPA Entities phải kế thừa từ `BaseEntity` (chứa `id`, `createdAt`, `updatedAt`).

---

## 5. Verification Checklist (Kiểm tra bắt buộc)

Sau khi chỉnh sửa code, AI **BẮT BUỘC** phải tự động chạy kiểm tra để đảm bảo ứng dụng biên dịch thành công trước khi hoàn tất:

```powershell
# Kiểm tra biên dịch Java
cd <service-folder>; .\gradlew compileJava; cd ..

# Chạy Unit Tests (nếu có)
cd <service-folder>; .\gradlew test; cd ..

# Build đóng gói JAR
cd <service-folder>; .\gradlew bootJar; cd ..
```

---

## 6. Git Branch & Commit Conventions (Quy chuẩn Git & Commit theo Jira Ticket)

Khi người dùng yêu cầu AI tạo nhánh, tạo commit hoặc push code lên GitHub, AI **BẮT BUỘC** phải tuân thủ các quy tắc sau:

> 💡 **Ghi chú về `<mã-task-jira>`:**
> - **Tên nhánh:** Ưu tiên sử dụng mã Main Task / Story / Bug ID (ví dụ: `TM-1`, `TM-2`) để quản lý theo tính năng hoặc lỗi tổng thể.
> - **Commit Message:** Ưu tiên sử dụng mã Sub-task / Sub-bug ID (nếu task/bug được chia nhỏ thành Sub-task trên Jira), hoặc mã Main Task / Bug ID (nếu làm việc trực tiếp trên Ticket chính).

### 🌿 Quy tắc đặt tên nhánh (Branch Naming)
Cấu trúc bắt buộc: `<type>/<mã-task-jira>/<tên-tính-năng>` (tên tính năng dùng kebab-case).
- `feature/<mã-task-jira>/<tên-tính-năng>`: Phát triển tính năng mới (ví dụ: `feature/TM-1/create-intern-profile`)
- `bugfix/<mã-task-jira>/<tên-lỗi>`: Sửa lỗi / Bugfix (ví dụ: `bugfix/TM-2/fix-update-phone-validation`)
- `refactor/<mã-task-jira>/<tên-mô-tả>`: Tối ưu hóa, cấu trúc lại code (ví dụ: `refactor/TM-1/intern-service-clean`)
- `test/<mã-task-jira>/<tên-mô-tả>`: Bổ sung kiểm thử / test suite (ví dụ: `test/TM-1/intern-controller-unit-test`)
- `chore/<mã-task-jira>/<tên-mô-tả>`: Cấu hình dependencies, Docker, CI/CD (ví dụ: `chore/TM-3/update-gradle-dependencies`)

### 💬 Quy tắc Commit Message (Conventional Commits)
Cấu trúc: `<type>(<mã-task-jira>): <nội dung mô tả ngắn gọn>`
- `feat(TM-1): bổ sung API thêm mới hồ sơ thực tập sinh`
- `fix(TM-2): sửa lỗi cập nhật thông tin hồ sơ thực tập sinh`
- `refactor(TM-1): tối ưu hóa query JPA lấy danh sách hồ sơ`
- `test(TM-1): bổ sung Unit Test và Integration Test cho module Intern`
- `docs(TM-5): cập nhật quy chuẩn Git Branch và Commit vào AGENTS.md`
- `chore(TM-6): cập nhật cấu hình build Gradle và Dockerfile`
