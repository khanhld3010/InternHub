# Technical Implementation Plan: TM-10 Đăng Ký Tài Khoản & Nộp Hồ Sơ Trực Tuyến

---

## 1. Overview & Architectural Context (Tổng Quan Kỹ Thuật)
- **Ticket:** [TM-10: Account Registration & Online Application](https://robluccibn9935.atlassian.net/browse/TM-10)
- **Subsystems Tác Động:**
  1. `identity-and-access-service` (Port 8081 - Quản lý tài khoản, mã hóa mật khẩu, phân quyền)
  2. `intern-and-program-service` (Port 8082 - Tiếp nhận hồ sơ, liên kết định danh người dùng)
  3. `api-gateway` (Port 8080 - Định tuyến public endpoints)
- **Change Level:** **L3**
- **Core Architecture:**
  - Áp dụng triệt để kiến trúc **Package-by-Feature** và nguyên tắc **Separation of Concerns**.
  - **Single Composite Payload Strategy:** Form đăng ký gửi 1 Object JSON tổng hợp duy nhất $\rightarrow$ Backend bóc tách tuần tự để lưu bảng `users` trước, sau đó lấy `user_id` sinh ra để tạo bản ghi trong bảng `accounts`.
  - **Atomic Transaction Guarantee:** Bao bọc toàn bộ logic bóc tách và ghi dữ liệu trong `@Transactional(rollbackFor = Exception.class)` $\rightarrow$ Chống triệt để việc sinh ra các bản ghi người dùng mồ côi (`Zero Orphaned Users`).
  - **Identity-Profile Decoupling:** Giữ ranh giới vi dịch vụ độc lập; `intern_profiles` chỉ lưu khóa mềm `user_id` (BIGINT) trỏ sang `users.id` của IAM Service mà không tạo foreign key vật lý chéo database.

---

## 2. Technical Decisions & Clarifications (Quyết Định Kỹ Thuật Đã Thống Nhất)
1. **Mô hình DTO đăng ký:** 
   - Sử dụng 1 DTO duy nhất `RegisterRequest` tiếp nhận toàn bộ thông tin cá nhân và thông tin tài khoản đăng nhập.
2. **Quy trình lưu trữ 2 bước tại IAM:**
   - **Pha 1:** Kiểm tra tính duy nhất (`username`, `email`, `phone`). Lưu bảng `users` $\rightarrow$ sinh `id`.
   - **Pha 2:** Lấy `user.getId()`, mã hóa mật khẩu BCrypt, gán role `"Intern"`, gán `status = "ACTIVE"` $\rightarrow$ Lưu bảng `accounts`.
3. **Mã hóa và An toàn mật khẩu:**
   - Sử dụng `BCryptPasswordEncoder` với chuẩn độ mạnh tối thiểu 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt.
4. **Mở rộng bảng `intern_profiles`:**
   - Bổ sung cột `user_id BIGINT NULL` và đánh index `idx_intern_user_id` để tối ưu hóa truy vấn tra cứu hồ sơ theo tài khoản đăng nhập.
5. **Cấu hình Security:**
   - `POST /api/auth/register` (hoặc `/api/employees/auth/register`) mở public (`permitAll()`).
   - `POST /api/interns/apply` mở public (`permitAll()`), hỗ trợ nhận `userId` từ body hoặc tự động trích xuất từ JWT token nếu đã đăng nhập.

---

## 3. Detailed Component Blueprint (Chi Tiết Thành Phần Triển Khai)

### 3.1. Phân hệ `identity-and-access-service` (Port 8081)

#### 3.1.1. DTO Layer (`org.example.employeeservice.dto`)
- **[NEW]** `request/RegisterRequest.java`:
  - `String fullName` (`@NotBlank`, `@Size(min=2, max=100)`)
  - `String email` (`@NotBlank`, `@Email`, `@Size(max=100)`)
  - `String phoneNumber` (`@NotBlank`, `@Pattern(regexp="(0[3|5|7|8|9])+([0-9]{8})\\b")`)
  - `LocalDate dateOfBirth` (`@JsonFormat(pattern="yyyy-MM-dd")`)
  - `Gender gender` (`MALE`, `FEMALE`, `OTHER`)
  - `String address` (`@Size(max=255)`)
  - `String avatarUrl` (`@Size(max=500)`)
  - `String username` (`@NotBlank`, `@Size(min=4, max=50)`, `@Pattern(regexp="^[a-zA-Z0-9_]+$")`)
  - `String password` (`@NotBlank`, `@Size(min=8, max=50)`, `@Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$")`)
- **[NEW]** `response/RegisterResponse.java`:
  - `Integer userId`, `String username`, `String fullName`, `String email`, `String role`, `String status`, `LocalDateTime createdAt`

#### 3.1.2. Audit Logging (`org.example.employeeservice.system.audit.entity`)
- **[MODIFY]** `AuditAction.java`:
  - Bổ sung enum `REGISTER` vào nhóm Auth actions:
    ```java
    // Auth actions
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    REGISTER,
    ```

#### 3.1.3. Service Layer (`org.example.employeeservice.service`)
- **[MODIFY]** `AuthService.java`:
  - Khai báo method: `RegisterResponse register(RegisterRequest request);`
- **[MODIFY]** `impl/AuthServiceImpl.java`:
  - Tiêm phụ thuộc qua Constructor: `UserRepository`, `AccountRepository`, `RoleRepository`, `PasswordEncoder`.
  - Triển khai logic `@Transactional(rollbackFor = Exception.class)`:
    1. Kiểm tra tồn tại `accountRepository.existsByUsername(request.getUsername())` $\rightarrow$ ném `DuplicateResourceException("Tên đăng nhập đã tồn tại trong hệ thống")`.
    2. Kiểm tra `userRepository.existsByEmail(request.getEmail())` $\rightarrow$ ném `DuplicateResourceException("Email đã được sử dụng")`.
    3. Kiểm tra `userRepository.existsByPhoneNumber(request.getPhoneNumber())` $\rightarrow$ ném `DuplicateResourceException("Số điện thoại đã được sử dụng")`.
    4. Tìm vai trò `Role internRole = roleRepository.findByName("Intern").orElseThrow(...)`.
    5. Tạo `User user = User.builder().fullName(...).email(...).phoneNumber(...)...build();` $\rightarrow$ `User savedUser = userRepository.save(user);`.
    6. Tạo `Account account = Account.builder().userId(savedUser.getId()).user(savedUser).username(request.getUsername()).passwordHash(passwordEncoder.encode(request.getPassword())).role(internRole).status("ACTIVE").build();` $\rightarrow$ `accountRepository.save(account);`.
    7. Trả về `RegisterResponse`.

#### 3.1.4. Controller Layer (`org.example.employeeservice.controller`)
- **[MODIFY]** `AuthController.java`:
  - Thêm endpoint:
    ```java
    @Auditable(action = AuditAction.REGISTER, module = AuditModule.AUTH, description = "Đăng ký tài khoản người dùng mới")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Nhận yêu cầu đăng ký tài khoản mới: username={}, email={}", request.getUsername(), request.getEmail());
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Đăng ký tài khoản thành công", response));
    }
    ```

---

### 3.2. Phân hệ `intern-and-program-service` (Port 8082)

#### 3.2.1. Entity Layer (`org.example.internservice.intern.entity`)
- **[MODIFY]** `InternProfile.java`:
  - Bổ sung trường:
    ```java
    @Column(name = "user_id")
    private Long userId;
    ```
  - Cập nhật builder/constructor tương ứng.

#### 3.2.2. DTO Layer (`org.example.internservice.intern.dto`)
- **[NEW]** `request/ApplyInternRequest.java`:
  - `Long userId` (tùy chọn)
  - `String fullName` (`@NotBlank`, `@Size(min=2, max=100)`)
  - `String email` (`@NotBlank`, `@Email`, `@Size(max=100)`)
  - `String phone` (`@NotBlank`, `@Pattern(regexp="(0[3|5|7|8|9])+([0-9]{8})\\b")`)
  - `LocalDate dateOfBirth` (`@JsonFormat(pattern="yyyy-MM-dd")`)
  - `Gender gender`
  - `String address` (`@Size(max=255)`)
  - `String university` (`@NotBlank`, `@Size(max=150)`)
  - `String major` (`@NotBlank`, `@Size(max=100)`)
  - `String academicYear` (`@Size(max=50)`)
  - `String appliedPosition` (`@NotBlank`, `@Size(max=100)`)
  - `LocalDate startDate` (`@NotNull`, `@JsonFormat(pattern="yyyy-MM-dd")`)
  - `LocalDate endDate` (`@JsonFormat(pattern="yyyy-MM-dd")`)
  - `String notes`
- **[MODIFY]** `response/InternResponse.java`:
  - Bổ sung `private Long userId;` để hiển thị liên kết tài khoản.

#### 3.2.3. Repository Layer (`org.example.internservice.intern.repository`)
- **[MODIFY]** `InternProfileRepository.java`:
  - Bổ sung các query method:
    - `boolean existsByUserIdAndStatusIn(Long userId, List<InternStatus> statuses);`
    - `boolean existsByEmailAndStatusIn(String email, List<InternStatus> statuses);`
    - `Optional<InternProfile> findByUserId(Long userId);`

#### 3.2.4. Audit Logging (`org.example.internservice.system.audit.entity`)
- **[MODIFY]** `AuditAction.java`:
  - Bổ sung enum `APPLY_INTERN` vào nhóm Intern actions:
    ```java
    // Intern actions
    CREATE_INTERN,
    UPDATE_INTERN,
    CHANGE_INTERN_STATUS,
    APPLY_INTERN,
    ```

#### 3.2.5. Service Layer (`org.example.internservice.intern.service`)
- **[MODIFY]** `InternProfileService.java`:
  - Khai báo method: `InternResponse applyOnline(ApplyInternRequest request);`
- **[MODIFY]** `impl/InternProfileServiceImpl.java`:
  - Triển khai logic `@Transactional`:
    1. Kiểm tra nếu `userId != null`: `existsByUserIdAndStatusIn(userId, List.of(PENDING, APPROVED, INTERNING))` $\rightarrow$ ném `BadRequestException("Bạn đã có một hồ sơ đang chờ xét duyệt hoặc đang trong quá trình thực tập")`.
    2. Kiểm tra nếu `email` đã có hồ sơ đang xử lý: `existsByEmailAndStatusIn(email, List.of(PENDING, APPROVED, INTERNING))` $\rightarrow$ ném `BadRequestException("Hồ sơ với email này đang được xử lý hoặc đang trong kỳ thực tập")`.
    3. Kiểm tra tính duy nhất của email & phone trên toàn bảng (nếu cần theo rule BR-2).
    4. Tự động sinh `internCode` định dạng `INT-YYYY-XXXX` (tận dụng hàm `generateInternCode()` sẵn có).
    5. Khởi tạo `status = InternStatus.PENDING`.
    6. Gán `userId` vào `InternProfile` $\rightarrow$ Gọi `internProfileRepository.save(profile)`.
    7. Map sang `InternResponse` trả về cho Client.

#### 3.2.6. Controller & Security Layer
- **[MODIFY]** `org.example.internservice.intern.controller.InternProfileController`:
  - Thêm endpoint:
    ```java
    @Auditable(action = AuditAction.APPLY_INTERN, module = AuditModule.INTERN, description = "Nộp hồ sơ ứng tuyển trực tuyến")
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<InternResponse>> applyOnline(@Valid @RequestBody ApplyInternRequest request) {
        log.info("Nhận hồ sơ ứng tuyển trực tuyến: email={}, position={}", request.getEmail(), request.getAppliedPosition());
        InternResponse response = internProfileService.applyOnline(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Nộp hồ sơ ứng tuyển thành công", response));
    }
    ```
- **[MODIFY]** `org.example.internservice.config.SecurityConfig`:
  - Cấu hình mở quyền public cho endpoint nộp hồ sơ trực tuyến:
    ```java
    .requestMatchers(HttpMethod.POST, "/api/interns/apply").permitAll()
    ```

---

## 4. Verification & Testing Strategy (Kế Hoạch Kiểm Thử)

1. **Kiểm tra biên dịch Java 17/21 qua Gradle Wrapper:**
   - Biên dịch IAM Service: `.\gradlew :identity-and-access-service:compileJava`
   - Biên dịch Intern Service: `.\gradlew :intern-and-program-service:compileJava`
2. **Kiểm thử tự động (Unit Test / Mockito):**
   - Viết Unit Test cho `AuthServiceImpl.register`:
     + Test đăng ký thành công: Lưu User ➔ Lấy ID ➔ Lưu Account liên kết.
     + Test đăng ký thất bại do trùng Username / Email / Phone.
     + Test cơ chế Rollback: Giả lập lỗi khi lưu Account, xác nhận User không được commit vào DB.
   - Viết Unit Test cho `InternProfileServiceImpl.applyOnline`:
     + Test nộp hồ sơ thành công có gắn `userId`.
     + Test từ chối khi tài khoản đã có hồ sơ đang ở trạng thái `PENDING`.
3. **Kiểm thử tích hợp trên DB thực tế (`internhub_db`):**
   - Khởi động service cục bộ.
   - Gửi request `POST /api/auth/register` qua cURL / Postman.
   - Kiểm tra trực tiếp bảng `users` và `accounts` trong MySQL.
   - Gửi request `POST /api/interns/apply` với `userId` vừa tạo.
   - Kiểm tra bảng `intern_profiles` có `user_id` và mã `INT-2026-XXXX`.
