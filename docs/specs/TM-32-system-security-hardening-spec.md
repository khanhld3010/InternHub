# TÀI LIỆU YÊU CẦU KỸ THUẬT: CẢI TIẾN & KHẮC PHỤC LỖ HỔNG BẢO MẬT HỆ THỐNG
## Mã tài liệu: TM-32-SYSTEM-SECURITY-HARDENING-SPEC
**Trạng thái**: Draft / Sẵn sàng triển khai  
**Dự án**: InternHub (Microservices Backend & React Frontend)  
**Tiêu chuẩn tuân thủ**: OWASP ASVS v4.0, NIST SP 800-63B, OAuth 2.1, RFC 7009  

---

## 1. MỤC TIÊU & PHẠM VI (SCOPE)
Tài liệu này xác định danh mục các lỗ hổng/bất cập bảo mật trong luồng xác thực (Authentication), quản lý phiên (Session Management) và lưu trữ Token trong hệ thống InternHub, cùng các nội dung chính xác cần sửa đổi trên cả Backend (`identity-and-access-service`, `api-gateway`) và Frontend (`InternHub-Frontend`).

---

## 2. DANH MỤC CÁC LỖI BẢO MẬT CẦN SỬA (SECURITY DEFECT CATALOG)

| Mã lỗi | Phân loại | Mức độ | Tóm tắt lỗi cần sửa | Vị trí ảnh hưởng |
| :--- | :--- | :---: | :--- | :--- |
| **SEC-01** | Token Architecture | 🔴 High | Access Token sống quá dài (24h) và thiếu cơ chế Refresh Token xoay vòng (Rotation) | `JwtProperties.java`, `JwtTokenProvider.java`, DB MySQL |
| **SEC-02** | Insecure Storage | 🔴 High | Lưu trữ Token & Profile trong `localStorage`, lộ trên DevTools và rủi ro XSS | `authService.ts`, `api.ts`, `LoginModal.tsx` |
| **SEC-03** | Session Life-cycle | 🟡 Medium | Đóng trình duyệt / tắt máy không xóa phiên (Lưu vĩnh viễn trên máy dùng chung) | `LoginRequest.java`, `AuthController.java`, Cookie flags |
| **SEC-04** | Token Revocation | 🔴 High | Đăng xuất (Logout) chỉ xóa ở Frontend, Backend không hề hủy Token (Zombie Token) | `AuthController.java`, `RefreshTokenService.java` |
| **SEC-05** | API Interceptor | 🟡 Medium | Frontend thiếu Interceptor cấp mới ngầm (Silent Refresh) và cơ chế khóa hàng đợi (Mutex Queue) | `api.ts`, `AuthContext.tsx` |

---

## 3. CHI TIẾT CÁC NỘI DUNG CẦN SỬA

### SEC-01: Kiến Trúc Token Kép (Dual-Token: Access 15m + Refresh 7d) & Token Rotation

#### 1. Vấn đề cần sửa:
* Hiện tại hệ thống chỉ sinh 1 Access Token duy nhất với thời gian sống 24h (`86400000ms`), không có Refresh Token trong database và không có cơ chế thu hồi.

#### 2. Nội dung cần sửa:
1. **Cấu hình thời gian sống (`JwtProperties.java`)**:
   * Giảm thời gian sống Access Token: `expiration = 900000L` (15 phút).
   * Thêm thời gian sống Refresh Token: `refreshExpiration = 604800000L` (7 ngày = 604.800.000 ms).
2. **Cơ sở dữ liệu (MySQL Table `refresh_tokens`)**:
   ```sql
   CREATE TABLE IF NOT EXISTS refresh_tokens (
       id BIGINT AUTO_INCREMENT PRIMARY KEY,
       account_id BIGINT NOT NULL,
       token_hash VARCHAR(64) NOT NULL UNIQUE,
       expiry_date DATETIME NOT NULL,
       revoked BOOLEAN NOT NULL DEFAULT FALSE,
       revoked_at DATETIME NULL,
       replaced_by_token_hash VARCHAR(64) NULL,
       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       CONSTRAINT fk_refresh_token_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
       INDEX idx_token_hash (token_hash),
       INDEX idx_account_id (account_id)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
   ```
3. **Mã nguồn Backend (`identity-and-access-service`)**:
   * Thêm Entity `RefreshToken.java` và Repository `RefreshTokenRepository.java`.
   * Thêm Service `RefreshTokenService.java` với các nghiệp vụ:
     * `createRefreshToken(Account account)`: Sinh chuỗi ngẫu nhiên an toàn (UUID/SecureRandom), băm SHA-256 lưu DB, trả về chuỗi token raw.
     * `verifyAndRotate(String rawRefreshToken)`:
       * Tìm bản ghi qua hash.
       * Nếu không thấy hoặc đã hết hạn $\rightarrow$ Ném lỗi `InvalidRefreshTokenException`.
       * **Phát hiện tái sử dụng (Reuse Detection)**: Nếu token đã có cờ `revoked == true` $\rightarrow$ Phát hiện xâm nhập, lập tức thu hồi toàn bộ refresh token thuộc về `account_id` này!
       * Nếu hợp lệ $\rightarrow$ Đánh dấu token hiện tại `revoked = true`, `revoked_at = NOW()`, cấp 1 cặp (Access Token mới + Refresh Token mới), cập nhật `replaced_by_token_hash`.
4. **API Endpoint Mới (`AuthController.java`)**:
   * `POST /api/auth/refresh-token`:
     * Input: Đọc từ Cookie `internhub_refresh_token` (hoặc body `{ refreshToken: string }` cho mobile/Postman).
     * Output: `{ success: true, data: { accessToken: "...", expiresIn: 900 } }` kèm Set-Cookie Refresh Token mới.

---

### SEC-02: Bảo Vệ Token & Chống Rò Rỉ Bằng In-Memory + `HttpOnly` Cookie

#### 1. Vấn đề cần sửa:
* Frontend lưu token và user profile vào `localStorage`. DevTools Application tab hiển thị rõ token, và bất kỳ mã XSS nào cũng lấy cắp được.

#### 2. Nội dung cần sửa:
1. **Lưu trữ Access Token trong In-Memory RAM**:
   * Tại Frontend (`AuthContext.tsx` & `api.ts`):
     * Lưu `accessToken` vào biến module in-memory và React Context state.
     * **Xóa bỏ hoàn toàn**: `localStorage.setItem('internhub_token', ...)`.
     * Khi reload F5 hoặc mở tab: Không có token trong localStorage $\rightarrow$ Frontend tự động gọi `POST /api/auth/refresh-token` qua Cookie ngầm để khôi phục phiên đăng nhập.
2. **Đóng gói Refresh Token vào `HttpOnly` Cookie**:
   * Tại Backend (`AuthController.java`):
     * Khi login thành công và khi rotate refresh token:
       ```java
       ResponseCookie refreshCookie = ResponseCookie.from("internhub_refresh_token", refreshToken)
           .httpOnly(true)
           .secure(false) // Đặt false cho môi trường dev localhost, true khi chạy HTTPS production
           .path("/api/auth") // Chỉ gửi cookie này khi gọi các endpoint /api/auth (refresh-token, logout)
           .sameSite("Lax")
           .maxAge(rememberMe ? 7 * 24 * 3600 : -1) // -1 là session cookie
           .build();
       response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
       ```
3. **Cấu hình Gateway & CORS**:
   * Đảm bảo `api-gateway` và `apiClient` phía Frontend cấu hình `withCredentials = true` để trình duyệt cho phép truyền nhận cookie an toàn.

---

### SEC-03: Tự Động Xóa Phiên Khi Đóng Trình Duyệt & Checkbox "Ghi Nhớ Đăng Nhập"

#### 1. Vấn đề cần sửa:
* Hiện tại tắt trình duyệt thì session vẫn giữ vĩnh viễn trong `localStorage`. Máy tính dùng chung có nguy cơ bị người sau sử dụng tiếp tài khoản.

#### 2. Nội dung cần sửa:
1. **DTO Đăng Nhập Backend**:
   * `LoginRequest.java`: Thêm trường `private Boolean rememberMe = false;`.
2. **Logic Phân Loại Cookie Session vs Persistent**:
   * Nếu `rememberMe == false`:
     * Cookie `internhub_refresh_token` có `maxAge(-1)` (hoặc không set `Max-Age`). Đây là **Session Cookie**, trình duyệt sẽ tự động xóa ngay khi tắt toàn bộ cửa sổ trình duyệt.
   * Nếu `rememberMe == true`:
     * Cookie có `maxAge(7 * 24 * 3600)` (sống 7 ngày trên ổ cứng).
3. **Frontend Giao Diện**:
   * `LoginModal.tsx`: Thêm checkbox UI:
     ```tsx
     <label className="flex items-center gap-2">
       <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} />
       <span>Ghi nhớ đăng nhập trên thiết bị này</span>
     </label>
     ```

---

### SEC-04: Đăng Xuất Toàn Diện (End-to-End Revocation) Tại Backend

#### 1. Vấn đề cần sửa:
* Khi user bấm Đăng xuất, Frontend chỉ xóa local, token cũ vẫn còn hiệu lực tại Backend.

#### 2. Nội dung cần sửa:
1. **API Endpoint Đăng Xuất (`AuthController.java`)**:
   * Tạo `POST /api/auth/logout`.
   * Đọc Refresh Token từ Cookie (hoặc body).
   * Gọi `refreshTokenService.revokeToken(refreshToken)`.
   * Gửi Header xóa Cookie khỏi trình duyệt:
     ```java
     ResponseCookie cleanCookie = ResponseCookie.from("internhub_refresh_token", "")
         .httpOnly(true)
         .secure(false)
         .path("/api/auth")
         .sameSite("Lax")
         .maxAge(0) // Xóa ngay lập tức
         .build();
     response.addHeader(HttpHeaders.SET_COOKIE, cleanCookie.toString());
     ```
2. **Frontend `authService.ts`**:
   * Sửa hàm `logout()` thành hàm bất đồng bộ:
     * Gửi request `POST /api/auth/logout` lên máy chủ.
     * Xóa sạch Access Token trong RAM và thông tin profile.
     * Chuyển hướng người dùng về trang chủ.

---

### SEC-05: Axios Silent Refresh Interceptor & Chống Race Condition (Mutex Queue)

#### 1. Vấn đề cần sửa:
* Khi Access Token (15 phút) hết hạn, nếu trang web gọi đồng thời nhiều API (ví dụ dashboard vừa tải danh sách, vừa tải thống kê, vừa tải thông báo), tất cả cùng nhận lỗi 401 và cùng gọi refresh đồng thời $\rightarrow$ Gây lỗi Token Reuse hoặc bắn lỗi ra màn hình.

#### 2. Nội dung cần sửa (`api.ts`):
1. **Cấu hình Axios Response Interceptor**:
   * Bổ sung biến trạng thái và hàng đợi request:
     ```typescript
     let isRefreshing = false;
     let failedQueue: Array<{
       resolve: (token: string) => void;
       reject: (error: unknown) => void;
     }> = [];
     ```
2. **Thuật toán xử lý 401**:
   * Khi gặp lỗi 401 từ API nghiệp vụ:
     * Nếu `isRefreshing === false`:
       * Đặt `isRefreshing = true`.
       * Gọi `POST /api/auth/refresh-token`.
       * Cấp Access Token mới vào RAM.
       * Duyệt `failedQueue`: Cho phép tất cả các request đang đợi tiếp tục thực thi với Access Token mới.
       * `isRefreshing = false`.
     * Nếu `isRefreshing === true`:
       * Tạo một `Promise` mới và đẩy vào `failedQueue`.
     * Nếu gọi refresh-token thất bại (Refresh Token cũng hết hạn):
       * Xóa sạch session, chuyển hướng về login với thông báo "Phiên làm việc đã kết thúc".

---

## 4. TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA)

* [ ] **AC-1**: Mở tab Application trong DevTools không thấy bất kỳ JWT Token nào trong `localStorage` hay `sessionStorage`.
* [ ] **AC-2**: Cookie `internhub_refresh_token` hiển thị cờ `HttpOnly = true`, `SameSite = Lax/Strict`. JavaScript trong console (`document.cookie`) không thể đọc được cookie này.
* [ ] **AC-3**: Access Token hết hạn sau 15 phút. Người dùng tiếp tục click trên giao diện thì request tự động thành công (Silent Refresh) mà không bị văng ra hay báo lỗi.
* [ ] **AC-4**: Nếu không tích "Ghi nhớ đăng nhập", tắt trình duyệt và mở lại trang web $\rightarrow$ Yêu cầu đăng nhập lại (Session Cookie đã bị xóa). Nếu có tích $\rightarrow$ Tự động vào hệ thống bình thường.
* [ ] **AC-5**: Bấm "Đăng xuất" $\rightarrow$ Backend đánh dấu token đã revoked; Cookie bị xóa; Copy token cũ ra Postman gọi API nghiệp vụ sẽ bị từ chối `401 Unauthorized`.
* [ ] **AC-6**: Không xảy ra xung đột (Race Condition) khi nhiều component cùng lúc gọi API khi Access Token vừa hết hạn.
