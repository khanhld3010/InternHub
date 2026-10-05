# Specification: Đăng Nhập Người Dùng Bằng Google OAuth2 (Google OAuth2 Login)

> **Trạng thái:** DRAFT / IN_REVIEW  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-30-google-oauth2-login-spec.md`  
> **Áp dụng quy tắc:** [Persistent Spec & Change Rationale](file:///d:/codegym_final_project/InternHub/.agents/04-development-guide.md)

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

> [!IMPORTANT]
> **BẮT BUỘC ĐIỀN ĐẦY ĐỦ**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic, API, validation hay database (từ cấp độ L2 trở lên), **bắt buộc** phải ghi thêm một dòng vào bảng này để giải trình lý do trước khi coi nhiệm vụ là hoàn tất.

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-09-30 | Senior Backend AI Pair-Programmer | `TM-30` | Tạo mới đặc tả L3 | Thiết kế đặc tả kỹ thuật tính năng Đăng nhập người dùng bằng Google OAuth2 (Google Sign-In) trên nền tảng Spring Boot Microservices, chuẩn hóa cơ chế ID Token Verification cho SPA Client, đồng bộ tài khoản tự động và bảo mật JWT nội bộ. |
| **v1.1** | 2026-09-30 | Senior Backend AI Pair-Programmer | `TM-30` | Siết chặt an toàn bảo mật (Privacy Policy) | Bổ sung chính sách phòng vệ đa tầng (Defense-in-Depth) chống lộ lọt Token và thông tin nhạy cảm trên DevTools (Network tab, Console, URL bar, Browser History) và hệ thống log kiểm toán máy chủ theo chỉ thị người dùng. |
| **v1.2** | 2026-09-30 | Senior Backend AI Pair-Programmer | `TM-30` | Áp dụng Rule #12 (Zero-Access `.env`) | Bổ sung ràng buộc tuân thủ Rule #12: Tuyệt đối nghiêm cấm AI đọc, sửa, phân tích file `.env`. Biến `GOOGLE_CLIENT_ID` nạp qua Spring `@Value` với fallback an toàn; người dùng tự quản lý file `.env` cá nhân. |
| **v1.3** | 2026-09-30 | Senior Backend AI Pair-Programmer | `TM-30` | Triển khai hoàn tất & Kiểm thử đạt 100% | Hoàn tất cài đặt các thành phần Google OAuth2 (`GoogleAuthController`, `GoogleOAuth2Service`, `GoogleTokenVerifierService`, `Account` Entity, `AuditLogAspect`) và chạy thành công 100% 11 Unit & Controller Tests. |
| **v1.4** | 2026-10-03 | Senior Backend AI Pair-Programmer | `TM-30` | Nâng cấp UX Silent Refresh Token Cookie | Bổ sung cấp phát HttpOnly Refresh Token Cookie (`internhub_refresh_token`) tự động khi đăng nhập bằng Google OAuth2 thành công, tương thích 100% với cơ chế Silent Refresh của SPA Client, giúp người dùng không bị gián đoạn phiên làm việc sau 15 phút. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)
- **Feature Name:** Đăng Nhập Người Dùng Bằng Google OAuth2 (Google OAuth2 Single Sign-On)
- **Jira Ticket:** [TM-30: Google OAuth2 Login & Account Synchronization](https://robluccibn9935.atlassian.net/browse/TM-30)
- **Target Microservice:** `identity-and-access-service` (Port 8081) & `api-gateway` (Port 8080)
- **Target Users & Roles:** Khách vãng lai, Sinh viên / Thực tập sinh (`ROLE_INTERN`), Cán bộ Quản trị / Nhân sự / Mentor (`ROLE_ADMIN`, `ROLE_HR`, `ROLE_MENTOR`)
- **Change Level:** **L3** (Tính năng xác thực mới, liên kết thực thể người dùng, bổ sung DTO, Service, Controller và mở rộng cấu trúc tài khoản)

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)
1. **Trải nghiệm đăng nhập 1 chạm (1-Click Login):** Giúp ứng viên, sinh viên thực tập và nhân sự có thể đăng nhập hoặc tạo tài khoản tức thì thông qua tài khoản Google Workspace hoặc Gmail cá nhân mà không cần trải qua quy trình điền form dài dòng và xác thực OTP email thủ công.
2. **Tăng tỷ lệ hoàn tất nộp hồ sơ ứng tuyển trực tuyến (TM-10):** Loại bỏ rào cản quên mật khẩu hoặc chậm trễ nhận mã OTP kích hoạt tài khoản qua email; nâng cao trải nghiệm ứng viên khi tiếp cận cổng tuyển dụng InternHub.
3. **Đồng nhất định danh & Bảo mật tập trung (Unified IAM):** Tự động phát hiện và liên kết email Google với hồ sơ người dùng đã có trong hệ thống; cấp phát cặp JWT Token nội bộ (`accessToken`) hoàn toàn đồng nhất với cơ chế đăng nhập truyền thống, đảm bảo tính tương thích trong suốt với toàn bộ hệ thống Microservices hiện có.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- Cung cấp API Public Endpoint `POST /api/auth/oauth2/google` (và alias `/api/employees/auth/oauth2/google`) tiếp nhận `idToken` từ Frontend.
- Xác thực chữ ký mã hóa (Cryptographic Verification) và tính hợp lệ của Google ID Token qua thư viện chính thức `com.google.api-client:google-api-client` bằng public keys được cache tự động từ Google.
- Kiểm tra tính xác thực của hòm thư Google (`email_verified == true`).
- Cơ chế liên kết tài khoản (Account Linking):
  - Nếu email đã tồn tại trong bảng `users`: Tự động liên kết, cập nhật `lastLoginAt`, cập nhật trạng thái từ `PENDING_ACTIVATION` sang `ACTIVE` (do Google đã xác thực quyền sở hữu email), cập nhật ảnh đại diện `avatar_url` nếu chưa có.
  - Nếu email chưa tồn tại: Tạo mới `User` và `Account` tương ứng trong cùng một Atomic Transaction (`@Transactional`), tự động sinh username duy nhất không trùng lặp, cấp mật khẩu ngẫu nhiên băm BCrypt, gán vai trò mặc định `Intern`, trạng thái `ACTIVE`.
- Sinh JWT Token nội bộ với cấu trúc Claims chuẩn (`sub`, `userId`, `role`, `accountId`).
- Trả về `LoginResponse` chuẩn hóa chứa `accessToken`, `role`, `username`, `fullName`, `email`, `userId`.
- Ghi nhận Audit Log: `action = AuditAction.LOGIN_SUCCESS` (hoặc `LOGIN_FAILED`), `module = AuditModule.AUTH`, mặt nạ hóa payload nhạy cảm.

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn chặn suy diễn sai*)
- **Không can thiệp mã nguồn Frontend (`InternHub-Frontend`):** Tuân thủ tuyệt đối **Rule #7 (Boundary Isolation)**. Tính năng chỉ cung cấp API Contract chuẩn hóa để đội ngũ Frontend tích hợp Google Identity Services (GIS SDK).
- **Không triển khai Server-side OAuth2 Session Redirect:** Không sử dụng luồng Authorization Code truyền thống chuyển hướng trình duyệt nhiều chặng (gây phức tạp về Cookie, Session và CORS trên API Gateway).
- **Không hỗ trợ đổi mật khẩu cho tài khoản thuần Google:** Tài khoản tạo qua Google không có mật khẩu người dùng biết; nếu muốn đặt mật khẩu, sẽ thuộc tính năng "Thiết lập mật khẩu lần đầu / Quên mật khẩu" trong các ticket chuyên biệt khác.

---

## 4. Potential Logic Loopholes & Mitigations (Tối thiểu 5 Edge Cases Cốt Lõi)

### 4.1. Case 1: Giả mạo hoặc hết hạn Google ID Token
- **Vấn đề:** Kẻ tấn công tự chế tạo một chuỗi JWT giả mạo hoặc gửi lại một token Google đã quá hạn để xâm nhập tài khoản của nạn nhân.
- **Giải pháp:** Sử dụng `GoogleIdTokenVerifier` với bộ xác thực cryptographic nghiêm ngặt:
  - Tự động tải và đối soát chữ ký với Google Public Certificates (JWKS).
  - Kiểm tra trường thời hạn `exp` (Token Expiration Time).
  - Kiểm tra trường phát hành `iss` (`accounts.google.com` hoặc `https://accounts.google.com`).
  - Nếu không hợp lệ, lập tức từ chối với `UnauthorizedException("Google ID Token không hợp lệ hoặc đã hết hạn")` (HTTP 401).

### 4.2. Case 2: Tấn công Audience Mismatch (Dùng Token của ứng dụng khác)
- **Vấn đề:** Kẻ tấn công lấy Google ID Token hợp lệ được cấp cho một ứng dụng bên thứ ba khác (ví dụ từ một trang web bán hàng) rồi gửi sang API của InternHub. Token này có chữ ký Google xịn nhưng không được phát hành cho InternHub.
- **Giải pháp:** Khai báo cấu hình `GOOGLE_CLIENT_ID` của dự án InternHub trong `GoogleIdTokenVerifier.setAudience(Collections.singletonList(clientId))`. Trình xác thực của Google sẽ tự động so khớp claim `aud` trong token với `clientId` của InternHub. Nếu không khớp, token bị từ chối ngay lập tức.

### 4.3. Case 3: Email Google chưa được kích hoạt (`email_verified = false`)
- **Vấn đề:** Người dùng tự tạo một tài khoản Google bằng email doanh nghiệp hoặc email tự chọn nhưng chưa hoàn tất bước nhấp link xác minh trong hộp thư. Kẻ xấu có thể đăng ký tài khoản Google bằng email của người khác và đăng nhập vào InternHub nhằm chiếm đoạt tài khoản.
- **Giải pháp:** Sau khi giải mã payload của Google ID Token, kiểm tra bắt buộc:
  ```java
  Boolean emailVerified = payload.getEmailVerified();
  if (emailVerified == null || !emailVerified) {
      throw new BadRequestException("Địa chỉ email Google chưa được xác minh. Vui lòng xác thực tài khoản Google trước khi tiếp tục.");
  }
  ```

### 4.4. Case 4: Trùng lặp username khi sinh tự động từ Email
- **Vấn đề:** Hai người dùng Google có tiền tố email giống nhau (ví dụ `nguyen.van.a@gmail.com` và `nguyen.van.a@fpt.edu.vn`). Khi trích xuất `nguyen.van.a` làm `username`, hệ thống sẽ ném `DuplicateResourceException` hoặc vướng ràng buộc `UNIQUE` trong bảng `accounts`.
- **Giải pháp:** Thiết kế thuật toán sinh username an toàn và sạch sẽ:
  - Làm sạch tiền tố email (loại bỏ ký tự đặc biệt, dấu chấm, dấu cộng: `nguyen.van.a` ➔ `nguyenvana`).
  - Cắt độ dài tối đa 30 ký tự.
  - Kiểm tra `accountRepository.existsByUsername(candidate)`.
  - Nếu đã tồn tại, tự động nối thêm hậu tố ngẫu nhiên dạng chuỗi ngắn: `candidate + "_" + randomHex(4)` (ví dụ: `nguyenvana_a7b2`) cho đến khi duy nhất.

### 4.5. Case 5: Người dùng có tài khoản đang bị khóa (`status = 'LOCKED'`)
- **Vấn đề:** Nhân sự hoặc thực tập sinh vi phạm chính sách đã bị Quản trị viên khóa tài khoản (`status = 'LOCKED'`). Người dùng này bấm "Đăng nhập bằng Google" để mong vượt rào đăng nhập.
- **Giải pháp:** Áp dụng chặt chẽ quy tắc bảo vệ trạng thái:
  - Khi tra cứu tài khoản đã tồn tại, nếu `account.getStatus().equalsIgnoreCase("LOCKED")`, ném ngay `UnauthorizedException("Tài khoản của bạn đã bị khóa hoặc vô hiệu hóa bởi Quản trị viên")`. Tuyệt đối không sinh JWT Token.

### 4.6. Case 6: Nguy cơ rò rỉ Token & Thông tin nhạy cảm lên DevTools (Network tab, Console, URL bar, Browser History)
- **Vấn đề:** 
  1. *Rò rỉ qua URL:* Nếu truyền token qua URL Callback (`/callback?token=...`), token sẽ hiện rõ mồn một trên thanh URL trình duyệt, bị lưu vào Browser History, lộ trên DevTools Network URL và bị rò rỉ qua HTTP Referer header khi người dùng nhấp link ngoài.
  2. *Rò rỉ qua Network Preview / Response Body:* Nếu Backend trả về các trường nội bộ như `password_hash`, Google `provider_id` (`sub`), hoặc ném raw Google API Exception khi lỗi, người dùng mở F12 Network tab xem Response Preview sẽ thấy toàn bộ thông tin nhạy cảm.
  3. *Rò rỉ qua Client Console:* Nếu Frontend dùng `console.log(response)` hoặc `console.log(credential)`, token sẽ bị in trực tiếp lên tab Console của DevTools.
  4. *Rò rỉ qua Server Access Log & Audit Log:* Nếu server ghi log raw request body không qua mặt nạ, token Google sẽ nằm lộ trong database `audit_logs` hoặc file log ổ cứng.
- **Giải pháp phòng vệ chuyên sâu (Defense-in-Depth):**
  1. **Không truyền Token qua URL:** 100% token được đóng gói an toàn trong **POST Request Body (JSON)** từ Client lên Backend và trả về qua **Response Body (JSON)**. Tuyệt đối không tạo bất kỳ endpoint nào nhận token qua Query Parameter.
  2. **Làm sạch DTO trả về (DTO Sanitization):** `LoginResponse` chỉ chứa các trường hiển thị an toàn (`accessToken`, `role`, `username`, `fullName`, `email`, `userId`). Tuyệt đối không đưa `passwordHash`, `providerId`, hoặc internal IDs vào DTO.
  3. **Mặt nạ hóa dữ liệu toàn diện (Data Masking in Logs):** Bổ sung `idtoken`, `credential`, `googletoken`, `activationkey`, `otp`, `refreshtoken`, `accesstoken` vào `DataMaskingUtils.java`. Mọi bản ghi `audit_logs.request_payload` tự động biến đổi thành `{"idToken": "******"}`.
  4. **Sanitized Error Responses (Chống rò rỉ qua thông báo lỗi):** Khi xác thực Google ID Token thất bại (sai chữ ký, hết hạn), `GlobalExceptionHandler` chỉ trả về thông điệp tổng quát: `"Google ID Token không hợp lệ hoặc đã hết hạn"`. Tuyệt đối không echo lại chuỗi token của người dùng, không để lộ exception trace của Google SDK.
  5. **Quy định Frontend Console Hygiene (Rule #7):** Khuyến nghị cấm sử dụng `console.log(credential)` hoặc `console.log(token)` trên mã nguồn Frontend, chỉ lưu token vào secure storage và xóa ngay biến tạm.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)
- **FR-1 (Public Google Login Endpoint):** Hệ thống cung cấp API `POST /api/auth/oauth2/google` cho phép gửi lên Google ID Token để đăng nhập mà không cần truyền Authorization header.
- **FR-2 (Verification & Extraction):** Hệ thống xác thực tính hợp lệ của token và trích xuất thông tin người dùng: `email`, `name`, `sub` (Google Subject ID), `picture` (ảnh đại diện).
- **FR-3 (Auto Account Linking):** Nếu email đã tồn tại trong bảng `users`, hệ thống tự động gắn kết với tài khoản `Account`, cập nhật `last_login_at`, chuyển trạng thái `PENDING_ACTIVATION` ➔ `ACTIVE`, và cấp quyền truy cập.
- **FR-4 (Auto Provisioning New User):** Nếu email chưa từng xuất hiện, hệ thống tự động khởi tạo hồ sơ `User` và `Account` với vai trò `Intern` (`ROLE_INTERN`), trạng thái `ACTIVE`, mật khẩu băm ngẫu nhiên bảo vệ dữ liệu.
- **FR-5 (Internal JWT Token Issuance):** Hệ thống trả về `LoginResponse` chứa JWT Token nội bộ có đầy đủ quyền hạn, hạn sử dụng và thông tin cá nhân.
- **FR-6 (Audit Logging):** Mọi lượt đăng nhập thành công hay thất bại qua Google OAuth2 đều được ghi nhận vào bảng `audit_logs` phục vụ giám sát và bảo mật theo chuẩn TM-9.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)
- **BR-1 (Verified Email Precondition):** Chỉ chấp nhận tài khoản Google có cờ `email_verified = true`.
- **BR-2 (Default Role Assignment):** Tài khoản đăng ký mới hoàn toàn qua Google mặc định mang vai trò `Intern` (ID tương ứng trong bảng `roles`). Người dùng mang vai trò quản trị viên (`HR`, `ADMIN`, `MENTOR`) bắt buộc phải được tạo tài khoản trước bởi hệ thống; khi họ đăng nhập Google cùng email đó, vai trò quản trị của họ được giữ nguyên vẹn.
- **BR-3 (Account Status Lifecycle):**
  - Tài khoản `ACTIVE`: Cho phép đăng nhập bình thường.
  - Tài khoản `PENDING_ACTIVATION`: Được chuyển ngay sang `ACTIVE` vì Google đã xác thực email.
  - Tài khoản `LOCKED`: Từ chối đăng nhập với HTTP 401.
- **BR-4 (Password Integrity):** Cột `password_hash` trong bảng `accounts` là `NOT NULL`. Tài khoản tạo qua Google sẽ được gán chuỗi hash an toàn dạng `BCrypt(UUID.randomUUID().toString())` nhằm chống đăng nhập trái phép bằng mật khẩu rỗng và đảm bảo toàn vẹn schema cơ sở dữ liệu.
- **BR-5 (Atomic Persistence):** Việc tạo mới đồng thời `User` và `Account` phải được bọc trong một `@Transactional(rollbackFor = Exception.class)`. Nếu bất kỳ thao tác nào gặp lỗi, toàn bộ transaction được hoàn tác, không để lại bản ghi rác mồ côi.
- **BR-6 (Zero Token Leakage & DevTools Privacy Enforcement):**
  - Không truyền bất kỳ token nào qua URL query parameters.
  - Không để lộ `passwordHash`, `providerId`, hoặc internal system traces trong Response JSON.
  - Mặt nạ hóa `******` 100% các trường token trong server logs và audit logs.

---

## 7. Data Model (Mô Hình Dữ Liệu)

### 7.1. Cập Nhật Bảng `accounts` (Không phá hoại schema hiện có)
| Tên cột | Kiểu dữ liệu | Nullable | Mặc định | Mô tả |
| :--- | :--- | :---: | :---: | :--- |
| `id` | `INT` | NO | AUTO_INCREMENT | Khóa chính của tài khoản |
| `user_id` | `INT` | NO | - | Khóa ngoại trỏ đến `users.id` (UNIQUE) |
| `role_id` | `INT` | NO | - | Khóa ngoại trỏ đến `roles.id` |
| `username` | `VARCHAR(50)` | NO | - | Tên đăng nhập duy nhất (UNIQUE) |
| `password_hash`| `VARCHAR(255)`| NO | - | Mật khẩu băm BCrypt |
| `status` | `VARCHAR(20)` | NO | `'ACTIVE'` | Trạng thái (`ACTIVE`, `PENDING_ACTIVATION`, `LOCKED`) |
| `auth_provider`| `VARCHAR(20)` | YES | `'LOCAL'` | Nguồn xác thực (`LOCAL`, `GOOGLE`) |
| `provider_id`  | `VARCHAR(100)`| YES | NULL | Định danh duy nhất từ Provider (Google `sub`) |
| `last_login_at`| `DATETIME` | YES | NULL | Thời điểm đăng nhập gần nhất |
| `created_at`   | `DATETIME` | YES | CURRENT_TIMESTAMP | Thời điểm tạo bản ghi |
| `updated_at`   | `DATETIME` | YES | CURRENT_TIMESTAMP | Thời điểm cập nhật bản ghi |

### 7.2. JPA Entity Mapping (`entity/Account.java`)
```java
@Column(name = "auth_provider", length = 20)
@Builder.Default
private String authProvider = "LOCAL";

@Column(name = "provider_id", length = 100)
private String providerId;
```

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

### Endpoint: `POST /api/auth/oauth2/google` (và alias `/api/employees/auth/oauth2/google`)
- **Authentication:** Public (`permitAll()`), không yêu cầu JWT Token.
- **Mô tả:** Tiếp nhận Google ID Token từ Frontend Single Page App, xác thực và trả về JWT Token nội bộ.

#### Request Headers:
```http
Content-Type: application/json
```

#### Request Body JSON:
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjFkMmUzZj...<Google_ID_Token>"
}
```

#### Response Success (HTTP 200 OK):
```json
{
  "success": true,
  "message": "Đăng nhập bằng tài khoản Google thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "username": "cuongle_g128",
    "fullName": "Lê Văn Cường",
    "email": "cuong.le@gmail.com",
    "role": "Intern",
    "userId": 15
  },
  "timestamp": "2026-09-30T14:30:00"
}
```

#### Response Errors:
- **HTTP 400 Bad Request (Thiếu token hoặc email chưa xác minh):**
  ```json
  {
    "code": 400,
    "message": "Địa chỉ email Google chưa được xác minh. Vui lòng xác thực tài khoản Google trước khi tiếp tục.",
    "timestamp": "2026-09-30T14:30:00"
  }
  ```
- **HTTP 401 Unauthorized (Token giả mạo, hết hạn, hoặc tài khoản bị khóa):**
  ```json
  {
    "code": 401,
    "message": "Google ID Token không hợp lệ hoặc đã hết hạn",
    "timestamp": "2026-09-30T14:30:00"
  }
  ```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)

```
[Client SPA]
     │
     ├── 1. POST /api/auth/oauth2/google { idToken }
     ▼
[API Gateway: Port 8080]
     │
     ├── 2. Route Path=/api/auth/** ➔ lb://identity-and-access-service
     ▼
[identity-and-access-service: Port 8081]
     │
     ├── 3. GoogleAuthController.loginWithGoogle(@Valid request)
     │       │
     │       ├── 4. GoogleTokenVerifierService.verify(idToken)
     │       │       ├── Gọi GoogleIdTokenVerifier kiểm tra chữ ký & client_id & exp
     │       │       ├── Trích xuất Payload: email, name, picture, sub, email_verified
     │       │       └── Nếu không hợp lệ ➔ Ném UnauthorizedException(401)
     │       │
     │       ├── 5. GoogleOAuth2Service.loginWithGoogle(request) [@Transactional]
     │       │       ├── Kiểm tra emailVerified == true (Nếu false ➔ 400)
     │       │       ├── userRepository.findByEmail(email)
     │       │       │
     │       │       ├── [NHÁNH A: EMAIL ĐÃ TỒN TẠI]
     │       │       │     ├── Lấy account = accountRepository.findByUserId(user.getId())
     │       │       │     ├── Kiểm tra status: nếu LOCKED ➔ Ném UnauthorizedException(401)
     │       │       │     ├── Nếu status == PENDING_ACTIVATION ➔ account.setStatus("ACTIVE")
     │       │       │     ├── account.setAuthProvider("GOOGLE"), account.setProviderId(sub)
     │       │       │     ├── account.setLastLoginAt(now)
     │       │       │     └── accountRepository.save(account)
     │       │       │
     │       │       └── [NHÁNH B: EMAIL CHƯA TỒN TẠI]
     │       │             ├── Tạo User: fullName = name, email = email, avatarUrl = picture
     │       │             ├── savedUser = userRepository.save(user)
     │       │             ├── Sinh username duy nhất (prefix_email + random suffix nếu trùng)
     │       │             ├── Tìm Role: "Intern" (hoặc fallback roleRepository)
     │       │             ├── Tạo Account: username, passwordHash = BCrypt(UUID), role, status="ACTIVE", authProvider="GOOGLE", providerId=sub, lastLoginAt=now
     │       │             └── accountRepository.save(account)
     │       │
     │       ├── 6. jwtTokenProvider.generateToken(account) ➔ Cấp JWT Token nội bộ
     │       └── 7. Map dữ liệu sang LoginResponse DTO
     │
     └── 8. Trả về ApiResponse.success("Đăng nhập bằng tài khoản Google thành công", response)
```

---

## 10. Non-Functional Requirements & Constraints
- **Performance:** Thao tác xác thực ID Token qua Google Public Certificates đạt độ trễ < 50ms nhờ cơ chế in-memory caching khóa công khai của `GoogleIdTokenVerifier`. Toàn bộ luồng API phản hồi < 150ms.
- **Stateless Architecture:** Tuyệt đối không lưu Session hoặc Cookie state trên máy chủ; 100% định danh tiếp tục duy trì qua HTTP Header `Authorization: Bearer <accessToken>`.
- **Anti-God-Class (Rule #16):** Tách biệt chức năng Google OAuth2 thành các class độc lập trong package `org.example.employeeservice.oauth2.*` (`GoogleAuthController`, `GoogleOAuth2Service`, `GoogleTokenVerifierService`), tuyệt đối không gộp quá tải vào `AuthServiceImpl.java`.
- **Constructor Injection (Rule #20):** Khai báo toàn bộ dependency là `private final` và dùng `@RequiredArgsConstructor` của Lombok. Không dùng `@Autowired` trên field.
- **Audit Compliance (TM-9):** Ghi nhật ký đầy đủ với `module = AuditModule.AUTH`, `action = LOGIN_SUCCESS/LOGIN_FAILED`.
- **Zero Token Leakage in DevTools & Logs (Chống rò rỉ DevTools & Logs):**
  - Không truyền bất kỳ token nào qua URL query parameters.
  - Loại bỏ hoàn toàn `passwordHash`, `providerId` (Google user id), và internal exception stacktrace khỏi response JSON.
  - Tự động che mờ `******` cho các trường `idToken`, `token`, `credential`, `otp` trong `audit_logs` và server log qua `DataMaskingUtils`.
  - Không echo lại token người dùng trong các thông báo lỗi HTTP 400/401/500.
- **Strict Zero-Access to `.env` (Rule #12):**
  - Tuyệt đối nghiêm cấm AI Agent và hệ thống mở, đọc, sửa, hoặc phân tích file `.env` của người dùng.
  - Mã nguồn Backend nạp biến qua `@Value("${app.oauth2.google.client-id:mock-google-client-id}")` hoặc properties class có giá trị fallback an toàn.
  - Khi cần đối soát môi trường, Agent bắt buộc thông báo để người dùng tự kiểm tra giúp.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)
- [x] **AC-1:** Gửi `idToken` Google hợp lệ với email chưa từng có trong hệ thống ➔ Tự động tạo `User`, tạo `Account` với vai trò `Intern`, trạng thái `ACTIVE`, trả về HTTP 200 kèm JWT token và thông tin tài khoản.
- [x] **AC-2:** Gửi `idToken` Google hợp lệ với email đã tồn tại ở trạng thái `PENDING_ACTIVATION` ➔ Kích hoạt thành `ACTIVE`, cập nhật `lastLoginAt`, trả về HTTP 200 và JWT token.
- [x] **AC-3:** Gửi `idToken` Google hợp lệ với email đã tồn tại mang vai trò `HR` hoặc `ADMIN` ➔ Giữ nguyên vai trò quản trị `HR`/`ADMIN`, đăng nhập thành công.
- [x] **AC-4:** Gửi `idToken` bị sai lệch chữ ký, hết hạn hoặc sai Audience Client ID ➔ Trả về HTTP 401 Unauthorized với thông điệp *"Google ID Token không hợp lệ hoặc đã hết hạn"*.
- [x] **AC-5:** Gửi `idToken` có `email_verified = false` ➔ Trả về HTTP 400 Bad Request.
- [x] **AC-6:** Gửi `idToken` thuộc tài khoản đang ở trạng thái `LOCKED` ➔ Trả về HTTP 401 Unauthorized thông báo tài khoản bị khóa.
- [x] **AC-7:** Gửi request rỗng hoặc `idToken` để trống ➔ Trả về HTTP 400 Bad Request kèm validation field error.
- [x] **AC-8 (Zero Token & PII Leakage in DevTools & Logs):**
  - DevTools Network tab: URL gọi API là `POST /api/auth/oauth2/google`, không có bất kỳ query param nào chứa token.
  - DevTools Response tab: Response Body hoàn toàn không chứa `password_hash`, không chứa `provider_id`.
  - Database Audit Logs: Cột `request_payload` trong bảng `audit_logs` hiển thị `{"idToken": "******"}`.
  - DevTools Console tab & Error Response: Không có log lỗi in chuỗi ID token dạng thô khi xác thực thất bại.
- [x] **AC-9 (Strict Zero-Access to `.env` Compliance - Rule #12):**
  - Không có bất kỳ dòng code, script, test case hay lệnh nào truy cập trực tiếp vào file `.env`.
  - 100% Unit Tests khởi tạo Google ID Token Verifier bằng mock/stub mà không phụ thuộc vào việc nạp file `.env`.

---

## 12. Unit & Integration Test Cases Checklist

### Unit Tests (`GoogleOAuth2ServiceTest.java`)
- [x] **UT-BE-01:** `givenValidGoogleIdToken_whenUserNotExists_thenProvisionUserAndAccountAndReturnLoginResponse()`
- [x] **UT-BE-02:** `givenValidGoogleIdToken_whenUserExistsPendingActivation_thenActivateAccountAndReturnLoginResponse()`
- [x] **UT-BE-03:** `givenValidGoogleIdToken_whenUserExistsActive_thenUpdateLastLoginAndReturnLoginResponse()`
- [x] **UT-BE-04:** `givenInvalidGoogleIdToken_whenLoginWithGoogle_thenThrowUnauthorizedException()`
- [x] **UT-BE-05:** `givenUnverifiedGoogleEmail_whenLoginWithGoogle_thenThrowBadRequestException()`
- [x] **UT-BE-06:** `givenLockedAccount_whenLoginWithGoogle_thenThrowUnauthorizedException()`
- [x] **UT-BE-07:** `givenDuplicateUsernamePrefix_whenProvisioning_thenGenerateUniqueSuffix()`
- [x] **UT-BE-08:** `givenGoogleLoginPayload_whenAuditing_thenEnsureIdTokenIsMaskedWithAsterisks()`

---

## 13. Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai)
- [x] **Data Masking (`DataMaskingUtils.java`):** Bổ sung `idtoken`, `credential`, `googletoken`, `activationkey`, `otp`, `code` vào danh sách từ khóa nhạy cảm bắt buộc che giấu (`******`).
- [x] **Dependencies (`build.gradle`):** Bổ sung `com.google.api-client:google-api-client:2.7.0` vào `identity-and-access-service`.
- [x] **Entity (`entity/Account.java`):** Thêm trường `authProvider` và `providerId`.
- [x] **Config (`config/GoogleOAuth2Properties.java`):** Nạp cấu hình `app.oauth2.google.client-id`.
- [x] **DTO (`oauth2/dto/request/GoogleLoginRequest.java`):** DTO chứa `idToken` có validate `@NotBlank`.
- [x] **Service Interface & Impl:**
  - [x] `oauth2/service/GoogleTokenVerifierService.java` & `impl/GoogleTokenVerifierServiceImpl.java`
  - [x] `oauth2/service/GoogleOAuth2Service.java` & `impl/GoogleOAuth2ServiceImpl.java`
- [x] **Controller (`oauth2/controller/GoogleAuthController.java`):** Endpoint `POST /api/auth/oauth2/google` có gắn `@Auditable`.
- [x] **Audit Integration (`AuditLogAspect.java`):** Bổ sung nhận diện `GoogleLoginRequest` trong hàm trích xuất username/email.
- [x] **Unit Tests (`service/GoogleOAuth2ServiceTest.java`):** Viết đầy đủ 8 ca test đơn vị đạt độ bao phủ 100%.
- [x] **Verification:** Chạy `.\gradlew.bat :identity-and-access-service:test` đạt 100% `BUILD SUCCESSFUL`.
