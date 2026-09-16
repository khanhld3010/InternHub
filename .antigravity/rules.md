# Backend AI Agent Guidelines (InternHub Backend)

Tập tin này định nghĩa quy tắc hoạt động, thứ tự nạp ngữ cảnh và quy chuẩn kỹ thuật dành cho AI Agent khi tham gia phát triển phần Backend của dự án **InternHub**.

---

## 1. Context Loading Order (Thứ tự nạp Ngữ cảnh)

Trước khi thực hiện bất kỳ nhiệm vụ nào (phát triển tính năng, sửa bug, refactor), AI **BẮT BUỘC** phải nạp và tuân thủ ngữ cảnh theo thứ tự sau:

1. 📖 **Tổng quan dự án & Vận hành:** Đọc [`README.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub/README.md) và [`tutorial.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub/tutorial.md) để nắm bức tranh tổng thể dự án Microservices, Docker, cổng dịch vụ.
2. 🔴 **Quy chuẩn kỹ thuật BẮT BUỘC:** Đọc `.antigravity/rules.md` (chính file này) chứa toàn bộ Coding Conventions, Naming Standards, Architecture Patterns và Git/Jira Conventions.
3. ⚙️ **Cấu hình tập trung:** Tham khảo thư mục [`config-repo-local/`](file:///c:/Users/Luong%20Anh%20Huy/InternHub/config-repo-local) khi cần nắm cấu hình các microservices (`employee-service.yml`, `api-gateway.yml`, `discovery-server.yml`).

---

## 2. Tech Stack & Môi trường Phát triển

- **Kiến trúc:** Microservices (Spring Cloud Gateway, Netflix Eureka Discovery Server, Spring Cloud Config Server).
- **Ngôn ngữ & Framework:** Java 17 / Java 21, Spring Boot 3.x / 4.x, Spring Data JPA, Spring Security / OAuth2 Resource Server, Spring Cloud.
- **Build Tool:** Gradle Wrapper (`.\gradlew` trên Windows, `./gradlew` trên Linux/macOS).
- **Database:** MySQL 8.0 (Container: `mysql-db:3306`, Host port: `3307`, Database: `internhub_db`).
- **Containers & Orchestration:** Docker, Docker Compose (`docker-compose.yml`).
- **Quản lý công việc (Jira):** Project Key `TM` (Team Management) trên Atlassian Cloud.

---

## 3. Skill Trigger Rules (Tự Động Kích Hoạt Skill)

AI cần tự động áp dụng các skill sau theo đúng loại tác vụ:

- **Khi thiết kế, tạo mới hoặc refactor REST API:** ➔ Áp dụng nguyên tắc RESTful API chuẩn mực, định danh tài nguyên số nhiều (ví dụ `/api/employees`, `/api/interns`).
- **Khi thảo luận, làm rõ ý tưởng, kiến trúc hoặc nghiệp vụ mới trước khi code:** ➔ Kích hoạt skill `brainstorming`.
- **Khi Refactor, tối ưu hóa code, hoặc sửa code chưa sạch:** ➔ Kích hoạt skill `codebase-cleanup-refactor-clean`.
- **Khi đánh giá, review code hoặc kiểm tra chất lượng Pull Request:** ➔ Kích hoạt skill `code-reviewer`.
- **Khi tạo/sửa JPA Entity, Repository, Query JPQL/SQL:** ➔ Tuân thủ Spring Data JPA best practices (tránh N+1 query, Lazy loading, phân trang Pageable).
- **Khi viết Unit Test / Integration Test:** ➔ Viết test JUnit 5, Mockito cho Service và `@WebMvcTest` cho Controller.

---

## 4. Nguyên Tắc Kiến Trúc & Coding Standards

### 4.1. Kiến trúc phân tầng & Package Structure
Gốc package: `org.example.employeeservice` (hoặc tương ứng theo từng microservice).
Tổ chức code theo hướng Feature / Clean Architecture:
- `entity/`: Chứa các JPA Entities.
- `repository/`: Spring Data JPA interfaces (`JpaRepository`, `JpaSpecificationExecutor`).
- `service/` & `service/impl/`: Chứa Interface và Implementation business logic.
- `controller/`: REST API endpoints. Chỉ nhận HTTP Request, validate DTO bằng `@Valid`, gọi Service, trả về DTO/ResponseEntity. **Tuyệt đối không viết business logic tại Controller**.
- `dto/`: Phân tách rõ ràng thành:
  - `dto/request/`: Các class Request DTO (ví dụ `CreateInternRequest`, `UpdateInternRequest`).
  - `dto/response/`: Các class Response DTO (ví dụ `InternResponse`, `InternDetailResponse`).
- `common/`: Chứa `BaseEntity`, enum chung, utility class.
- `exception/`: Chứa `GlobalExceptionHandler` và các custom exceptions (`ResourceNotFoundException`, `BadRequestException`, `DuplicateResourceException`).

### 4.2. Nguyên tắc Coding bắt buộc
- **Không trả JPA Entity trực tiếp ra Controller:** Luôn luôn map Entity qua DTO (Response DTO) trước khi trả về Client.
- **Dependency Injection:** Sử dụng Constructor Injection thông qua `@RequiredArgsConstructor` của Lombok (hoặc constructor tường minh). **TUYỆT ĐỐI KHÔNG DÙNG `@Autowired` trên field**.
- **Entities & BaseEntity:** Tất cả Entity phải extend `BaseEntity` (chứa `id`, `createdAt`, `updatedAt`, `@PrePersist`, `@PreUpdate`).
- **Tên bảng & Cột trong Database:** Tên bảng dùng chữ thường, số nhiều, snake_case (ví dụ: `intern_profiles`, `employees`).
- **Lombok:** Dùng `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` hợp lý. Tránh lạm dụng `@Data` trên Entity hai chiều để tránh đệ quy `hashCode/equals/toString`.
- **Transaction:** Đánh dấu `@Transactional(readOnly = true)` ở cấp độ Class ServiceImpl và `@Transactional` trên các method ghi/sửa/xóa dữ liệu.

---

## 5. Quy tắc Cấu trúc Code Java & Import

- **Quản lý Import:** LUÔN LUÔN sử dụng câu lệnh `import` tường minh ở đầu file cho tất cả các class, entity, DTO, hoặc utility.
- **TUYỆT ĐỐI KHÔNG** viết trực tiếp đường dẫn package đầy đủ (Fully Qualified Name - FQN) trong thân code (ví dụ: KHÔNG viết `org.example.employeeservice.entity.InternProfile profile = ...`).
- **Cách làm đúng:**
  ```java
  import org.example.employeeservice.entity.InternProfile;

  // Trong thân class:
  InternProfile profile = new InternProfile();
  ```

---

## 6. Verification Checklist (Kiểm tra bắt buộc)

Sau khi tạo hoặc chỉnh sửa code Java, AI **BẮT BUỘC** phải tự động chạy lệnh kiểm tra biên dịch trước khi thông báo hoàn tất:

```powershell
# 1. Kiểm tra biên dịch Java của service đang làm việc (ví dụ employee-service)
cd employee-service; .\gradlew compileJava; cd ..

# 2. Chạy test nếu có
cd employee-service; .\gradlew test; cd ..

# 3. Đóng gói JAR
cd employee-service; .\gradlew bootJar; cd ..
```

---

## 7. Git Branch & Commit Conventions (Chuẩn hóa với Jira Project `TM`)

Mọi nhánh và commit đều phải liên kết chặt chẽ với Jira Issue key trong project **`TM`** (ví dụ `TM-1`, `TM-2`).

### 7.1. Quy tắc đặt tên nhánh (Branch Naming)
Cấu trúc bắt buộc: `<type>/<mã-task-jira>/<tên-tính-năng-kebab-case>`
- `feature/TM-1/create-intern-profile` : Phát triển tính năng thêm mới hồ sơ thực tập sinh.
- `feature/TM-2/edit-intern-profile` : Phát triển tính năng chỉnh sửa hồ sơ thực tập sinh.
- `bugfix/TM-X/<tên-lỗi>` : Sửa lỗi chức năng.
- `refactor/TM-X/<tên-mô-tả>` : Tối ưu hóa, dọn dẹp cấu trúc code.
- `test/TM-X/<tên-mô-tả>` : Viết Unit Test / Integration Test.
- `chore/TM-X/<tên-mô-tả>` : Cấu hình Gradle, Docker, dependencies.

### 7.2. Quy tắc viết Commit Message (Conventional Commits)
Cấu trúc: `<type>(<mã-task-jira>): <nội dung mô tả ngắn gọn>`
- `feat(TM-1): thêm API tạo mới hồ sơ thực tập sinh`
- `feat(TM-2): thêm API cập nhật thông tin hồ sơ thực tập sinh`
- `fix(TM-3): sửa lỗi validate số điện thoại khi đăng ký hồ sơ`
- `refactor(TM-1): chuẩn hóa DTO response cho module intern profile`
- `test(TM-1): bổ sung Unit Test cho InternProfileService`

---

## 8. Forbidden Actions & Files

- **Không tự ý thêm dependency ngoài** chưa được bàn bạc hoặc không cần thiết vào `build.gradle`.
- **Không được tự ý sửa file cấu hình nhạy cảm** hoặc các file `.env` (nếu có).
- **Không commit mật khẩu / secret hardcode** vào mã nguồn; ưu tiên lấy từ biến môi trường qua `application.yml` hoặc `config-server`.

---

## 9. Session Summary Format

Cuối mỗi phiên làm việc, AI phải tóm tắt ngắn gọn:
1. **Đang làm gì?**
2. **Đã làm xong gì?**
3. **Quyết định kỹ thuật đã chốt?**
4. **Task tiếp theo cần làm?**
