# Technical Implementation Plan: TM-10 Đăng Ký Tài Khoản, Xác Thực Email & Nộp Hồ Sơ Trực Tuyến
*(Phiên bản nâng cấp: v1.4 - Tích hợp xác thực email bằng mã OTP kích hoạt tài khoản)*

---

## 1. Overview & Architectural Context (Tổng Quan Kỹ Thuật v1.4)
- **Ticket:** [TM-10: Account Registration, Email Verification & Online Application](https://robluccibn9935.atlassian.net/browse/TM-10)
- **Subsystems Tác Động:**
  1. `identity-and-access-service` (Port 8081 - Quản lý tài khoản, vòng đời kích hoạt, mã OTP, xác thực đăng nhập)
  2. `reporting-and-integration-service` (Port 8083 - Tiếp nhận REST yêu cầu gửi email, quản lý template HTML thương hiệu InternHub, gửi mail bất đồng bộ qua SMTP / Mock mode)
  3. `intern-and-program-service` (Port 8082 - Tiếp nhận hồ sơ trực tuyến, liên kết định danh người dùng)
  4. `api-gateway` (Port 8080 - Định tuyến public endpoints `/api/auth/**`)
- **Change Level:** **L3**
- **Core Architecture Principles:**
  - **Separation of Concerns & Code Reuse:** Tái sử dụng triệt để hạ tầng email sẵn có của `reporting-and-integration-service` (`EmailDeliveryService`, `EmailTemplateBuilder`, `email_logs`, `mockMode`). Dịch vụ IAM giữ vai trò quản lý tài khoản & token, giao tiếp với Reporting Service qua REST Client (`IntegrationEmailClient`), không cần cài đặt thư viện mail SMTP riêng.
  - **Environment-Driven Secrets (.env Management):** Toàn bộ thông tin nhạy cảm (SMTP credentials, JWT secret, DB password) bắt buộc nạp qua file `.env`, tuyệt đối không hardcode trong mã nguồn.
  - **Client-Side Data Masking & Token Hiding:** Tuyệt đối không trả về mã kích hoạt (Activation Key / OTP) trong bất kỳ API response nào của Client (chỉ trả về `maskedEmail` dạng `in***@gmail.com`). Ngăn chặn triệt để nguy cơ người dùng xem trộm mã qua Network DevTools / Browser Inspection.
  - **Resilience & Graceful Degradation:** Lỗi hoặc độ trễ từ dịch vụ gửi email không làm ảnh hưởng hoặc rollback transaction tạo tài khoản.

---

## 2. Technical Decisions & Clarifications (Quyết Định Kỹ Thuật Đã Thống Nhất)

1. **Trạng thái khởi tạo của tài khoản:**
   - Khi đăng ký mới: `Account.status = "PENDING_ACTIVATION"`.
   - Người dùng chưa thể đăng nhập chừng nào tài khoản chưa được kích hoạt thành `ACTIVE`.
2. **Cơ chế mã kích hoạt (Activation OTP):**
   - Sử dụng chuỗi ngẫu nhiên 6 chữ số an toàn được sinh bởi `java.security.SecureRandom`.
   - Thời hạn hiệu lực: **15 phút**.
   - Lưu trữ trong bảng mới `account_activation_tokens`.
3. **Phòng chống tấn công dò mã (Brute-Force Protection):**
   - Mỗi token có biến đếm `attempt_count`.
   - Mỗi lần nhập sai: `attempt_count += 1`.
   - Khi `attempt_count >= 5`: Hủy hiệu lực của mã, yêu cầu người dùng phải yêu cầu gửi lại mã mới.
4. **Phòng chống tấn công Spam Hòm thư (Email Bombing / Spam Resend):**
   - Áp dụng cơ chế **Cooldown 60 giây**: Chỉ cho phép gọi API gửi lại mã `/api/auth/resend-activation` sau khi lần gửi trước đã trôi qua ít nhất 60s.
5. **Giao tiếp liên dịch vụ (Inter-service Client Pattern):**
   - Tái sử dụng pattern `IntegrationEmailClient` (tương tự như bên `intern-and-program-service`) sử dụng `RestTemplate` bắn request sang `http://reporting-and-integration-service:8083/api/integration/emails/account-activation`.

---

## 3. Detailed Component Blueprint (Chi Tiết Thành Phần Triển Khai)

### 3.1. Phân hệ `reporting-and-integration-service` (Port 8083)

#### 3.1.1. DTO Layer (`org.example.reportingservice.email.dto.request`)
- **[NEW]** `SendAccountActivationEmailRequest.java`:
  ```java
  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
  public class SendAccountActivationEmailRequest {
      @NotBlank(message = "Email không được để trống")
      @Email(message = "Email không đúng định dạng")
      private String email;

      @NotBlank(message = "Họ và tên không được để trống")
      private String fullName;

      @NotBlank(message = "Mã kích hoạt không được để trống")
      private String activationKey;

      private Integer expiresInMinutes; // Mặc định 15 phút
      private String idempotencyKey;    // Chống gửi trùng lặp
  }
  ```

#### 3.1.2. Template Builder (`org.example.reportingservice.email.template`)
- **[MODIFY]** `EmailTemplateBuilder.java`:
  - Thêm phương thức `buildAccountActivationEmail(String fullName, String activationKey, Integer expiresInMinutes)`:
    - Tận dụng layout `wrapEmail(...)` có sẵn (tông màu chuẩn `#4F46E5`, khung viền mờ, chống vỡ giao diện).
    - Hộp hiển thị mã OTP font monospace lớn, giãn cách `letter-spacing: 8px`.
    - Thông tin cảnh báo thời hạn 15 phút và khuyến cáo an toàn.

#### 3.1.3. Email Delivery Service (`org.example.reportingservice.email.service`)
- **[MODIFY]** `EmailDeliveryService.java`:
  - Thêm phương thức `@Async public void sendAccountActivationEmailAsync(SendAccountActivationEmailRequest request)`:
    - Tận dụng hàm `sendSingleEmail(...)` sẵn có.
    - Hỗ trợ `mockMode`: Nếu chưa cấu hình mật khẩu SMTP, in mã ra Console phục vụ test/dev.
    - Tự động ghi nhật ký vào bảng `email_logs`.

#### 3.1.4. Controller Layer (`org.example.reportingservice.email.controller`)
- **[MODIFY]** `EmailIntegrationController.java`:
  - Bổ sung endpoint:
    ```java
    @PostMapping("/account-activation")
    @Operation(summary = "Tiếp nhận yêu cầu gửi email xác thực kích hoạt tài khoản")
    public ResponseEntity<ApiResponse<Void>> sendAccountActivationEmail(
            @Valid @RequestBody SendAccountActivationEmailRequest request) {
        log.info("Tiếp nhận yêu cầu gửi email kích hoạt cho: {}", request.getEmail());
        emailDeliveryService.sendAccountActivationEmailAsync(request);
        return ResponseEntity.ok(ApiResponse.success(200, "Đã tiếp nhận yêu cầu gửi email thành công", null));
    }
    ```

---

### 3.2. Phân hệ `identity-and-access-service` (Port 8081)

#### 3.2.1. CSDL & Entity Layer (`org.example.employeeservice.entity`)
- **[NEW]** `AccountActivationToken.java`:
  ```java
  @Entity
  @Table(name = "account_activation_tokens")
  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
  public class AccountActivationToken {
      @Id
      @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;

      @Column(name = "account_id", nullable = false)
      private Integer accountId;

      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "account_id", insertable = false, updatable = false)
      private Account account;

      @Column(name = "activation_key", nullable = false, length = 64)
      private String activationKey;

      @Column(name = "token_type", nullable = false, length = 20)
      @Builder.Default
      private String tokenType = "REGISTER_ACTIVATION";

      @Column(name = "expires_at", nullable = false)
      private LocalDateTime expiresAt;

      @Column(name = "consumed_at")
      private LocalDateTime consumedAt;

      @Column(name = "attempt_count", nullable = false)
      @Builder.Default
      private Integer attemptCount = 0;

      @Column(name = "created_at", insertable = false, updatable = false)
      private LocalDateTime createdAt;
  }
  ```

#### 3.2.2. Repository Layer (`org.example.employeeservice.repository`)
- **[NEW]** `AccountActivationTokenRepository.java`:
  - `Optional<AccountActivationToken> findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(Integer accountId);`
  - `Optional<AccountActivationToken> findFirstByAccountIdAndActivationKeyAndConsumedAtIsNull(Integer accountId, String activationKey);`

#### 3.2.3. Inter-service Client Layer (`org.example.employeeservice.client`)
- **[NEW]** `IntegrationEmailClient.java`:
  - Sử dụng `RestTemplate` bắn request sang `reporting-and-integration-service`.
  - Đọc URL từ `${REPORTING_SERVICE_URL:http://reporting-and-integration-service:8083}`.
  - Bọc `try-catch` để đảm bảo lỗi mạng khi gửi mail không làm rollback transaction tạo tài khoản.

#### 3.2.4. DTO Layer (`org.example.employeeservice.dto`)
- **[MODIFY]** `response/RegisterResponse.java`:
  - Bổ sung trường: `private String maskedEmail;` (chỉ hiển thị email che mờ, không trả về mã OTP).
- **[NEW]** `request/ActivateAccountRequest.java`:
  ```java
  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
  public class ActivateAccountRequest {
      @NotBlank(message = "Email hoặc tên đăng nhập không được để trống")
      private String identifier;

      @NotBlank(message = "Mã kích hoạt không được để trống")
      @Pattern(regexp = "^[0-9]{6}$", message = "Mã kích hoạt phải gồm 6 chữ số")
      private String activationKey;
  }
  ```
- **[NEW]** `request/ResendActivationRequest.java`:
  ```java
  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
  public class ResendActivationRequest {
      @NotBlank(message = "Email hoặc tên đăng nhập không được để trống")
      private String identifier;
  }
  ```

#### 3.2.5. Audit Logging (`org.example.employeeservice.system.audit.entity`)
- **[MODIFY]** `AuditAction.java`:
  - Bổ sung enum:
    ```java
    ACTIVATE_SUCCESS,
    ACTIVATE_FAILED,
    RESEND_ACTIVATION,
    ```

#### 3.2.6. Service Layer (`org.example.employeeservice.service`)
- **[MODIFY]** `AuthService.java`:
  - Bổ sung các phương thức:
    - `void activateAccount(ActivateAccountRequest request);`
    - `void resendActivation(ResendActivationRequest request);`
- **[MODIFY]** `impl/AuthServiceImpl.java`:
  1. Cập nhật hàm `register()`:
     - Tạo `Account` với trạng thái `status = "PENDING_ACTIVATION"`.
     - Sinh mã OTP 6 số: `String.format("%06d", new SecureRandom().nextInt(1_000_000))`.
     - Lưu token vào `account_activation_tokens` (hạn 15 phút).
     - Gọi `integrationEmailClient.sendActivationEmail(user.getEmail(), user.getFullName(), activationKey, 15)`.
     - Trả về `RegisterResponse` kèm `maskedEmail` (VD: `ngu***@gmail.com`), **ẩn hoàn toàn mã OTP**.
  2. Triển khai hàm `activateAccount()`:
     - Tìm Account theo username hoặc user email.
     - Nếu đã `ACTIVE` $\rightarrow$ thông báo thành công.
     - Lấy token mới nhất chưa tiêu thụ.
     - Nếu `attemptCount >= 5` $\rightarrow$ báo lỗi quá số lần thử, yêu cầu gửi lại mã mới.
     - Nếu `expiresAt < now()` $\rightarrow$ báo lỗi hết hạn.
     - So khớp:
       + Sai: tăng `attemptCount += 1`, báo lỗi kèm số lần thử còn lại.
       + Đúng: `consumedAt = now()`, `account.status = "ACTIVE"`, lưu DB, ghi log `ACTIVATE_SUCCESS`.
  3. Triển khai hàm `resendActivation()`:
     - Kiểm tra token gần nhất, nếu tạo cách đây chưa đủ 60s $\rightarrow$ ném lỗi Cooldown.
     - Hủy token cũ, sinh token mới và gọi client gửi lại email.

#### 3.2.7. Controller & Security Layer
- **[MODIFY]** `AuthController.java`:
  - Bổ sung endpoint `POST /api/auth/activate` và `POST /api/auth/resend-activation`.
- **[MODIFY]** `SecurityConfig.java`:
  - Cấu hình mở quyền public (`permitAll()`) cho `/api/auth/activate` và `/api/auth/resend-activation`.

---

## 4. Verification & Testing Strategy (Kế Hoạch Kiểm Thử v1.4)

1. **Kiểm tra An toàn Biến môi trường (.env):**
   - Xác nhận file `.env` chứa đầy đủ cấu hình SMTP và Secrets, nằm trong `.gitignore`.
2. **Kiểm tra Bảo mật Client-Side:**
   - Gửi request `POST /api/auth/register` qua Postman/cURL hoặc Browser.
   - Xác nhận trong Response Body và Response Headers **hoàn toàn không chứa mã OTP**; chỉ có `maskedEmail`.
3. **Kiểm thử Tự động (Unit Test / Mockito):**
   - `testRegister_shouldCreatePendingAccountAndDispatchEmail()`
   - `testActivate_whenValidOtp_shouldChangeStatusToActive()`
   - `testActivate_whenInvalidOtp_shouldIncrementAttempts()`
   - `testActivate_whenExceed5Attempts_shouldRejectAndInvalidateToken()`
   - `testActivate_whenOtpExpired_shouldReject()`
   - `testResendActivation_whenWithinCooldown60s_shouldReject()`
   - `testResendActivation_whenAfterCooldown60s_shouldGenerateNewOtp()`
4. **Kiểm thử Gửi Email thực tế / Mock Console:**
   - Chạy với `MAIL_MOCK_MODE=true`: Xác nhận nội dung email và mã OTP in ra Console rõ ràng.
   - Thử nghiệm gửi qua SMTP thật (Gmail) để xác nhận email tới hòm thư người dùng.
