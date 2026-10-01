# Báo Cáo Triển Khai: TM-32 Cải Tiến & Khắc Phục Lỗ Hổng Bảo Mật Hệ Thống (Walkthrough)

> **Ticket:** [TM-32: System Security Hardening & Session Lifecycle](https://robluccibn9935.atlassian.net/browse/TM-32)  
> **Tài liệu đặc tả:** [TM-32-system-security-hardening-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-32-system-security-hardening-spec.md) (v2.1)  
> **Kế hoạch triển khai:** [TM-32-system-security-hardening-plan.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-32-system-security-hardening-plan.md)  
> **Trạng thái:** ✅ **BACKEND & FRONTEND HOÀN TẤT 100% & BIÊN DỊCH THÀNH CÔNG**

---

## 1. Tóm Tắt Các Hạng Mục Đã Hoàn Thành Phía Backend

Tuân thủ nghiêm ngặt **30 nguyên tắc bất biến của Backend** trong [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md):

### 1.1. Cấu Hình Thời Gian Sống Token
* **Tập tin:** [JwtProperties.java](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/config/JwtProperties.java)
  * Rút ngắn Access Token: `expiration = 900000L` (15 phút).
  * Bổ sung Refresh Token: `refreshExpiration = 604800000L` (7 ngày).
  * Bổ sung cờ cấu hình `cookieSecure = false` (tự động bật `true` qua biến môi trường khi lên Production HTTPS).
* **Tập tin:** [identity-and-access-service.yml](file:///d:/codegym_final_project/InternHub/config-repo-local/identity-and-access-service.yml)
  * Đồng bộ `jwt.expiration: ${JWT_EXPIRATION:900000}` và `jwt.refresh-expiration: ${JWT_REFRESH_EXPIRATION:604800000}`.

### 1.2. Domain Entity & Repository (Tuân thủ Rule #15)
* **Tập tin:** [RefreshToken.java](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/entity/RefreshToken.java)
  * Kế thừa [BaseEntity](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/common/entity/BaseEntity.java) để tự động quản lý `id`, `createdAt`, `updatedAt`.
  * Liên kết `@ManyToOne(fetch = FetchType.LAZY)` với `Account`.
  * Lưu trữ `tokenHash` (SHA-256), `expiryDate`, `revoked`, `revokedAt`, `replacedByTokenHash`.
* **Tập tin:** [RefreshTokenRepository.java](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/repository/RefreshTokenRepository.java)
  * Cung cấp các phương thức: `findByTokenHash`, `revokeAllByAccountId` (phục vụ Replay Attack Detection), `deleteExpiredTokens`.

### 1.3. Service Layer Chống God-Class (Tuân thủ Rule #17, #21, #23)
* **Tập tin:** [RefreshTokenService.java](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/service/RefreshTokenService.java) & [RefreshTokenServiceImpl.java](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/service/impl/RefreshTokenServiceImpl.java)
  * Module hóa độc lập, kích thước < 160 dòng, không làm phình to `AuthServiceImpl`.
  * Sinh raw token 64 ký tự hex ngẫu nhiên qua `SecureRandom` và băm một chiều SHA-256 trước khi lưu DB.
  * **Cơ chế Replay Attack Detection**: Nếu phát hiện token đã có `revoked == true` được gửi lên để refresh $\rightarrow$ lập tức thu hồi toàn bộ phiên của tài khoản đó (`revokeAllAccountTokens`) và ném lỗi 401.
  * **Cơ chế Token Rotation**: Token cũ bị revoke, ghi nhận `replaced_by_token_hash`, cấp cặp token mới.
  * Tự động dọn dẹp token rác định kỳ qua `@Scheduled(cron = "0 0 2 * * ?")`.

### 1.4. DTOs & REST Endpoints
* **Tập tin:** [LoginRequest.java](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/dto/request/LoginRequest.java): Thêm trường `Boolean rememberMe`.
* **Tập tin:** [RefreshTokenRequest.java](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/dto/request/RefreshTokenRequest.java) & [RefreshTokenResponse.java](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/dto/response/RefreshTokenResponse.java).
* **Tập tin:** [AuthController.java](file:///d:/codegym_final_project/InternHub/identity-and-access-service/src/main/java/org/example/employeeservice/controller/AuthController.java):
  * Cập nhật `POST /api/auth/login`: Cấp Access Token trong Body và HttpOnly Cookie `internhub_refresh_token`.
  * Thêm `POST /api/auth/refresh-token`: Đọc Cookie (hoặc body), xoay vòng token, trả về Access Token mới và Cookie mới.
  * Thêm `POST /api/auth/logout`: Thu hồi token trong database, gửi Set-Cookie `maxAge = 0` để xóa cookie trình duyệt.

### 1.5. Cấu Hình Gateway CORS Credentials
* **Tập tin:** [api-gateway.yml](file:///d:/codegym_final_project/InternHub/config-repo-local/api-gateway.yml)
  * Bổ sung `spring.cloud.gateway.globalcors` cho phép `allowCredentials: true` với origins `http://localhost:5173` và `http://localhost:3000`.

---

## 2. Kết Quả Kiểm Thử & Biên Dịch Phía Backend (Rule #27)

| Hạng mục kiểm tra | Lệnh thực thi | Kết quả |
| :--- | :--- | :---: |
| **Java Compilation** | `.\gradlew :identity-and-access-service:compileJava` | ✅ **BUILD SUCCESSFUL** (14s) |
| **Unit Tests TM-32** | `.\gradlew :identity-and-access-service:test --tests "org.example.employeeservice.service.RefreshTokenServiceImplTest"` | ✅ **5/5 PASSED** (14s) |
| **Regression Tests Auth** | `.\gradlew :identity-and-access-service:test --tests "org.example.employeeservice.service.AuthServiceTest"` | ✅ **ALL PASSED** (11s) |
| **Gateway Compilation** | `.\gradlew :api-gateway:compileJava` | ✅ **BUILD SUCCESSFUL** (7s) |

---

## 3. Tóm Tắt Các Hạng Mục Đã Hoàn Thành Phía Frontend

Theo yêu cầu trực tiếp của người dùng, toàn bộ kiến trúc Session & Token Security đã được triển khai hoàn chỉnh trên Frontend:

### 3.1. Types & Endpoints
* **Tập tin:** [src/types/auth.types.ts](file:///d:/codegym_final_project/InternHub-Frontend/src/types/auth.types.ts)
  * Bổ sung `rememberMe?: boolean` vào interface `LoginRequest`.
* **Tập tin:** [src/constants/endpoints/auth.endpoints.ts](file:///d:/codegym_final_project/InternHub-Frontend/src/constants/endpoints/auth.endpoints.ts)
  * Khai báo endpoint `REFRESH_TOKEN: '/api/auth/refresh-token'`.

### 3.2. Core API Client (In-Memory Token & Mutex Interceptor)
* **Tập tin:** [src/services/api.ts](file:///d:/codegym_final_project/InternHub-Frontend/src/services/api.ts)
  * **In-Memory Closure Token:** `getAccessToken()` và `setAccessToken()` lưu token trong RAM biến cục bộ, triệt tiêu hoàn toàn lỗ hổng XSS đánh cắp Access Token từ `localStorage`.
  * **WithCredentials:** Thiết lập `withCredentials: true` trên Axios instance để tự động đính kèm HttpOnly Cookie `internhub_refresh_token` qua Gateway.
  * **Mutex Request Queue chống Race Condition:** Khi Access Token hết hạn (401), nếu có nhiều API đồng thời phát sinh:
    * Chỉ 1 request đầu tiên đứng ra gọi `POST /api/auth/refresh-token`.
    * Các request sau được đẩy vào hàng đợi `failedQueue` dạng Promise.
    * Khi refresh thành công, toàn bộ hàng đợi được retry tự động với Access Token mới.
    * Nếu refresh thất bại (401/403 do token bị thu hồi hoặc hết hạn), phát sự kiện `AUTH_LOGOUT_EVENT` để kích hoạt đăng xuất tập trung.

### 3.3. Auth Service & Session Lifecycle
* **Tập tin:** [src/services/authService.ts](file:///d:/codegym_final_project/InternHub-Frontend/src/services/authService.ts)
  * Loại bỏ hoàn toàn việc đọc/ghi Access Token vào `localStorage`.
  * Hàm `login()`: Nhận `accessToken` từ backend và lưu vào bộ nhớ RAM (`setAccessToken`).
  * Bổ sung `refreshToken()`: Gọi endpoint `/api/auth/refresh-token` gửi Cookie ngầm, cập nhật In-Memory Access Token.
  * Cập nhật `logout()`: Chuyển thành async function, gọi `POST /api/auth/logout` để Backend revoke token trong DB và xóa HttpOnly Cookie (`maxAge = 0`).

### 3.4. State Management & Silent Session Restoration
* **Tập tin:** [src/contexts/AuthContext.tsx](file:///d:/codegym_final_project/InternHub-Frontend/src/contexts/AuthContext.tsx)
  * Quản lý cờ trạng thái `isInitializing: boolean` (khởi tạo ban đầu là `true`).
  * Khi App khởi động hoặc người dùng F5 tải lại trang:
    * `useEffect` tự động gọi ngầm `authService.refreshToken()` thông qua Cookie HttpOnly.
    * Nếu phiên còn hạn, lấy thông tin `getMe()` và khôi phục trạng thái đăng nhập tự động mà người dùng không cần đăng nhập lại.
    * Nếu không có Cookie hoặc phiên hết hạn, kết thúc khởi tạo bình thường (`isInitializing = false`).
  * Lắng nghe `AUTH_LOGOUT_EVENT` để tự động dọn dẹp state và chuyển hướng về màn hình đăng nhập khi bị từ chối phiên.
* **Tập tin:** [src/routes/ProtectedRoute.tsx](file:///d:/codegym_final_project/InternHub-Frontend/src/routes/ProtectedRoute.tsx) & [src/routes/AppRoutes.tsx](file:///d:/codegym_final_project/InternHub-Frontend/src/routes/AppRoutes.tsx)
  * Thêm kiểm tra `if (isInitializing) return <Spinner />;` để tránh tình trạng "flash redirect" về trang Login khi AuthContext đang thực hiện khôi phục phiên ngầm.

### 3.5. Giao Diện Người Dùng (Remember Me)
* **Tập tin:** [src/components/auth/LoginModal.tsx](file:///d:/codegym_final_project/InternHub-Frontend/src/components/auth/LoginModal.tsx)
  * Bổ sung state `rememberMe` và checkbox UI "Ghi nhớ đăng nhập trên thiết bị này".
  * Gửi thuộc tính `rememberMe` cùng payload đăng nhập đến Backend.
* **Tập tin:** [src/components/auth/LoginModal.module.css](file:///d:/codegym_final_project/InternHub-Frontend/src/components/auth/LoginModal.module.css)
  * Tạo kiểu dáng checkbox CSS Modules chuyên nghiệp, chuẩn CSS Variables, tương thích cả Dark/Light mode.

---

## 4. Kết Quả Kiểm Thử & Biên Dịch Phía Frontend

| Lệnh kiểm tra | Thư mục thực thi | Kết quả | Chi tiết |
| :--- | :--- | :---: | :--- |
| `npm run build` | `InternHub-Frontend` | ✅ **EXIT CODE 0** | `tsc -b` pass 100%, Vite đóng gói `dist/assets/index-DUy8Y493.js` (974 kB) trong 8.07s |

---

## 5. Tổng Kết Kiến Trúc Bảo Mật Đạt Được

1. **Phòng chống XSS (Cross-Site Scripting):**
   * Access Token có thời hạn ngắn (15 phút), chỉ tồn tại trong bộ nhớ RAM (In-Memory), tuyệt đối không lưu trong `localStorage` hay `sessionStorage`.
   * Refresh Token có thời hạn dài (7 ngày), được bảo vệ bằng `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, ngăn chặn JavaScript độc hại truy cập.

2. **Phòng chống Replay Attack (Tấn công phát lại Token):**
   * Token Rotation: Mỗi lần đổi Access Token mới, Refresh Token cũ bị vô hiệu hóa ngay lập tức.
   * Nếu kẻ tấn công dùng lại Refresh Token cũ đã bị vô hiệu hóa, Backend tự động kích hoạt **Cascade Revocation** thu hồi toàn bộ phiên của tài khoản đó.

3. **Trải nghiệm người dùng mượt mà (Seamless UX):**
   * Tự động làm mới phiên ngầm khi Access Token hết hạn thông qua Axios Mutex Queue mà không làm gián đoạn request của người dùng.
   * Tự động khôi phục phiên đăng nhập khi F5/mở lại tab thông qua Silent Session Restoration.
