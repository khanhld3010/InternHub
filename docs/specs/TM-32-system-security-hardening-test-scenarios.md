# Kịch Bản Kiểm Thử: TM-32 Cải Tiến & Khắc Phục Lỗ Hổng Bảo Mật Hệ Thống (Test Scenarios)

> **Ticket:** [TM-32: System Security Hardening & Session Lifecycle](https://robluccibn9935.atlassian.net/browse/TM-32)  
> **Tài liệu đặc tả:** [TM-32-system-security-hardening-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-32-system-security-hardening-spec.md) (v2.1)  
> **Walkthrough:** [TM-32-system-security-hardening-walkthrough.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-32-system-security-hardening-walkthrough.md)  
> **Mục tiêu:** Hướng dẫn chi tiết từng bước kiểm thử thực tế từ Unit Test, API Testing (Postman/cURL), Kiểm thử UI/Browser DevTools đến Kiểm thử thâm nhập tấn công bảo mật (Penetration / Security Tests).

---

## 1. Thông Tin & Môi Trường Kiểm Thử

| Thành phần | Địa chỉ / Cổng | Ghi chú |
| :--- | :--- | :--- |
| **API Gateway** | `http://localhost:8080` | Điểm tiếp nhận request duy nhất của Frontend |
| **Identity Service** | `http://localhost:8081` | Quản lý xác thực, token và database |
| **Frontend Web App** | `http://localhost:5173` | Ứng dụng React + Vite |
| **MySQL Database** | `localhost:3306` (internhub_db) | Bảng `refresh_tokens`, `accounts` |
| **Công cụ hỗ trợ** | Chrome DevTools, Postman / cURL, PowerShell | |

---

## 2. Kịch Bản 1: Kiểm Thử Tự Động (Automated Unit & Build Tests)

### TC-AUTO-01: Chạy Bộ Unit Test Refresh Token Service (Backend)
* **Mục đích:** Đảm bảo thuật toán sinh token, băm SHA-256, xoay vòng và phát hiện tấn công replay hoạt động chính xác ở tầng cô lập.
* **Cách thực hiện:** Mở PowerShell tại thư mục `InternHub`:
  ```powershell
  .\gradlew :identity-and-access-service:test --tests "org.example.employeeservice.service.RefreshTokenServiceImplTest"
  ```
* **Kết quả kỳ vọng:**
  * ✅ Cả 5/5 test cases đều **PASSED**:
    1. `givenValidAccount_whenCreateRefreshToken_thenReturnValidRawTokenAndSaveHashInDb` (Tạo token và lưu hash)
    2. `givenValidRawToken_whenVerifyAndRotate_thenRevokeOldTokenAndIssueNewPair` (Xoay vòng token hợp lệ)
    3. `givenExpiredToken_whenVerifyAndRotate_thenThrowUnauthorizedException` (Từ chối token hết hạn)
    4. `givenRevokedToken_whenVerifyAndRotate_thenTriggerReplayAttackDetectionAndRevokeAll` (Phát hiện Replay Attack và cascade revoke)
    5. `givenValidToken_whenRevokeToken_thenMarkRevokedTrue` (Thu hồi khi logout)

### TC-AUTO-02: Kiểm Tra Biên Dịch Type-Check & Đóng Gói (Frontend)
* **Mục đích:** Xác nhận toàn bộ mã nguồn Frontend tuân thủ nghiêm ngặt TypeScript `verbatimModuleSyntax` và CSS Modules.
* **Cách thực hiện:** Mở PowerShell tại thư mục `InternHub-Frontend`:
  ```powershell
  npm run build
  ```
* **Kết quả kỳ vọng:**
  * ✅ Lệnh kết thúc với **Exit Code 0**.
  * ✅ `tsc -b` không báo bất kỳ lỗi type nào.
  * ✅ Vite xuất ra thư mục `dist/` thành công.

---

## 3. Kịch Bản 2: Kiểm Thử Lưu Trữ Token & Chống XSS (Browser Storage & Cookies)

### TC-SEC-01: Kiểm Tra Không Lưu Token Tại LocalStorage / SessionStorage
* **Mục đích:** Đảm bảo Access Token và Refresh Token không bị lộ qua `window.localStorage`, loại bỏ hoàn toàn bề mặt tấn công của mã độc XSS.
* **Các bước thực hiện:**
  1. Mở trình duyệt Chrome/Edge, bấm `F12` mở DevTools, chuyển sang tab **Application**.
  2. Tại menu bên trái, mở rộng mục **Storage** $\rightarrow$ bấm vào **Local storage** $\rightarrow$ `http://localhost:5173`.
  3. Bấm vào **Session storage** $\rightarrow$ `http://localhost:5173`.
  4. Thực hiện đăng nhập vào hệ thống tại form Login.
* **Kết quả kỳ vọng:**
  * ✅ Trong cả `Local storage` và `Session storage`, **tuyệt đối KHÔNG có** bất kỳ key nào chứa chuỗi JWT (như `token`, `accessToken`, `jwt`, `refreshToken`).
  * ✅ Token chỉ được lưu trữ trong biến RAM (In-Memory closure của file `api.ts`).

### TC-SEC-02: Kiểm Tra Thuộc Tính HttpOnly và SameSite Của Cookie
* **Mục đích:** Đảm bảo JavaScript trong trang web không thể đọc hoặc đánh cắp Refresh Token qua `document.cookie`.
* **Các bước thực hiện:**
  1. Trong DevTools, tab **Application** $\rightarrow$ mục **Cookies** $\rightarrow$ chọn `http://localhost:5173` (hoặc `http://localhost:8080`).
  2. Tìm Cookie có tên `internhub_refresh_token`.
  3. Quan sát các cột thuộc tính:
     * Cột **HttpOnly**: Phải có dấu tích `✔` (chỉ cho phép HTTP truyền tải, cấm JS đọc).
     * Cột **SameSite**: Giá trị hiển thị `Lax` (hoặc `Strict`).
     * Cột **Path**: Giá trị `/api/auth`.
  4. Chuyển sang tab **Console**, gõ lệnh sau rồi nhấn Enter:
     ```javascript
     document.cookie
     ```
* **Kết quả kỳ vọng:**
  * ✅ Chuỗi in ra màn hình Console **hoàn toàn trống** hoặc không chứa `internhub_refresh_token`.

---

## 4. Kịch Bản 3: Kiểm Thử Tính Năng "Ghi Nhớ Đăng Nhập" (Remember Me)

### TC-FE-01: Đăng Nhập Không Tích "Ghi Nhớ Đăng Nhập" (Session Cookie)
* **Các bước thực hiện:**
  1. Mở modal Đăng nhập.
  2. Nhập Email và Password hợp lệ.
  3. **Không tích** vào ô checkbox *"Ghi nhớ đăng nhập trên thiết bị này"*.
  4. Bấm "Đăng nhập".
  5. Mở DevTools > **Application** > **Cookies** > xem cột **Expires / Max-Age** của cookie `internhub_refresh_token`.
  6. **Đóng hoàn toàn tất cả cửa sổ trình duyệt** (Quit browser).
  7. Mở lại trình duyệt và truy cập vào `http://localhost:5173`.
* **Kết quả kỳ vọng:**
  * ✅ Ở bước 5: Cột Expires/Max-Age ghi nhận giá trị `Session`.
  * ✅ Ở bước 7: Trình duyệt đã tự động hủy Session Cookie. Ứng dụng hiển thị ở trạng thái chưa đăng nhập (Nút "Đăng nhập", không tự động vào Dashboard).

### TC-FE-02: Đăng Nhập Có Tích "Ghi Nhớ Đăng Nhập" (Persistent Cookie 7 Ngày)
* **Các bước thực hiện:**
  1. Mở modal Đăng nhập.
  2. Nhập Email và Password hợp lệ.
  3. **Tích chọn** vào ô checkbox *"Ghi nhớ đăng nhập trên thiết bị này"*.
  4. Bấm "Đăng nhập".
  5. Mở DevTools > **Application** > **Cookies** > xem cột **Expires / Max-Age**.
  6. Tải lại trang (F5) hoặc đóng trình duyệt mở lại.
* **Kết quả kỳ vọng:**
  * ✅ Ở bước 5: Cột Expires/Max-Age có mốc thời gian cụ thể là **7 ngày tới** (khoảng `604800` giây).
  * ✅ Ở bước 6: Ứng dụng hiển thị spinner khởi tạo rất nhanh và tự động khôi phục phiên (**Silent Session Restoration**), người dùng vẫn ở trạng thái đăng nhập bình thường.

---

## 5. Kịch Bản 4: Kiểm Thử Xoay Vòng Token (Token Rotation) & Chống Race Condition (Mutex Queue)

### TC-ROT-01: Tự Động Làm Mới Token Khi Access Token Hết Hạn (Silent Refresh)
* **Mục đích:** Người dùng đang sử dụng ứng dụng mà Access Token (15 phút) hết hạn thì hệ thống tự động đổi token mới ngầm mà không làm gián đoạn người dùng.
* **Các bước thực hiện:**
  1. Đăng nhập vào ứng dụng.
  2. Mở tab **Network** trong DevTools, lọc từ khóa `auth`.
  3. Đợi 15 phút (hoặc cấu hình tạm `jwt.expiration: 15000` (15s) trong môi trường test để thử nhanh).
  4. Thực hiện một thao tác gọi API trên giao diện (ví dụ bấm xem trang Thông tin cá nhân, xem tin tuyển dụng,...).
* **Kết quả kỳ vọng:**
  * ✅ Request nghiệp vụ đầu tiên trả về mã `401 Unauthorized`.
  * ✅ Axios Response Interceptor tự động gửi request `POST /api/auth/refresh-token`.
  * ✅ Request refresh thành công trả về `200 OK` và cấp Access Token mới vào biến RAM + Cookie mới.
  * ✅ Request nghiệp vụ ban đầu tự động được gửi lại (retry) và trả về `200 OK`.
  * ✅ Giao diện người dùng hiển thị dữ liệu mượt mà, không xuất hiện popup báo lỗi.

### TC-ROT-02: Kiểm Thử Mutex Queue Chống Race Condition Khi Nhiều API Cùng Nhận 401
* **Mục đích:** Đảm bảo khi nhiều component cùng lúc gọi API mà token hết hạn, chỉ có **duy nhất 1 request refresh token** được gửi đi, các request còn lại phải xếp hàng chờ.
* **Các bước thực hiện:**
  1. Đăng nhập vào ứng dụng.
  2. Mở tab **Console** trong DevTools.
  3. Chạy đoạn mã sau để giả lập 5 request API đồng thời khi Access Token chưa có / hết hạn:
     ```javascript
     // Bắn 5 request đồng thời qua Axios client
     Promise.all([
       window.fetch('/api/users/me'),
       window.fetch('/api/notifications'),
       window.fetch('/api/jobs'),
       window.fetch('/api/applications'),
       window.fetch('/api/companies')
     ]);
     ```
  4. Quan sát tab **Network**.
* **Kết quả kỳ vọng:**
  * ✅ Chỉ có **duy nhất 1** request `POST /api/auth/refresh-token` xuất hiện trong tab Network.
  * ✅ Không xảy ra lỗi đụng độ dữ liệu (Không có request refresh nào bị lỗi 401 do xung đột xoay vòng).
  * ✅ Sau khi request refresh hoàn tất, cả 5 request đều nhận được token mới và hoàn thành.

---

## 6. Kịch Bản 5: Kiểm Thử Phát Hiện Tấn Công Phát Lại (Replay Attack Detection)

### TC-SEC-03: Kẻ Gian Sử Dụng Lại Refresh Token Cũ Đã Xoay Vòng
* **Mục đích:** Kiểm tra cơ chế tự vệ tối cao: Khi phát hiện một Refresh Token đã bị thu hồi (`revoked = true`) được gửi lên, hệ thống phải **thu hồi toàn bộ phiên** của tài khoản đó.
* **Các bước thực hiện qua Postman hoặc cURL:**

#### Bước 1: Đăng nhập lấy Cookie ban đầu
Gửi request đăng nhập:
```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@internhub.com","password":"Password123@","rememberMe":true}'
```
* **Lưu lại:** Giá trị Cookie `internhub_refresh_token` trả về trong Header `Set-Cookie` (Gọi là **Token_A**).

#### Bước 2: Thực hiện xoay vòng token hợp lệ lần thứ nhất
Gửi request refresh bằng **Token_A**:
```bash
curl -i -X POST http://localhost:8080/api/auth/refresh-token \
  -H "Cookie: internhub_refresh_token=TOKEN_A_GIA_TRI_THAT"
```
* **Kết quả bước 2:** Thành công `200 OK`. Hệ thống cấp Cookie mới (Gọi là **Token_B**).
* **Kiểm tra DB:**
  ```sql
  SELECT id, token_hash, revoked, replaced_by_token_hash FROM refresh_tokens WHERE account_id = (SELECT id FROM accounts WHERE email = 'admin@internhub.com');
  ```
  $\rightarrow$ Bản ghi của **Token_A** có `revoked = 1`, `replaced_by_token_hash` là hash của Token_B.

#### Bước 3: Kẻ tấn công gửi lại Token_A (Tấn công Replay)
Dùng cURL gửi lại chính **Token_A** cũ:
```bash
curl -i -X POST http://localhost:8080/api/auth/refresh-token \
  -H "Cookie: internhub_refresh_token=TOKEN_A_GIA_TRI_THAT"
```
* **Kết quả bước 3 (Kỳ vọng):**
  * ✅ Backend trả về mã lỗi `401 Unauthorized`.
  * ✅ Log Backend ghi nhận: `SECURITY ALERT: Refresh token reuse detected for account ... Cascade revoking all tokens!`
  * ✅ Kiểm tra lại Database:
    ```sql
    SELECT id, token_hash, revoked FROM refresh_tokens WHERE account_id = (SELECT id FROM accounts WHERE email = 'admin@internhub.com');
    ```
    **Tất cả các dòng** của account này (kể cả Token_B hợp lệ của người dùng) đều đã bị chuyển thành `revoked = 1`.

#### Bước 4: Kiểm tra phiên của người dùng hợp lệ
Thử dùng **Token_B** để gọi refresh:
```bash
curl -i -X POST http://localhost:8080/api/auth/refresh-token \
  -H "Cookie: internhub_refresh_token=TOKEN_B_GIA_TRI_THAT"
```
* **Kết quả:** Trả về `401 Unauthorized`. Phiên của người dùng đã bị hủy hoàn toàn để đảm bảo an toàn tuyệt đối.

---

## 7. Kịch Bản 6: Kiểm Thử Đăng Xuất (Logout & Revocation)

### TC-AUTH-01: Đăng Xuất Toàn Diện (Backend DB & Browser Cookie)
* **Các bước thực hiện:**
  1. Người dùng đang đăng nhập trên giao diện web.
  2. Bấm vào Menu tài khoản $\rightarrow$ chọn **"Đăng xuất"**.
  3. Quan sát tab **Network** và **Application** trong DevTools.
* **Kết quả kỳ vọng:**
  * ✅ Có request `POST /api/auth/logout` được gửi đi và trả về `200 OK`.
  * ✅ Header phản hồi có `Set-Cookie: internhub_refresh_token=; Max-Age=0; Expires=...; Path=/api/auth; HttpOnly`.
  * ✅ Cookie `internhub_refresh_token` trong tab Application lập tức biến mất.
  * ✅ In-memory Access Token trong RAM bị xóa về `null`.
  * ✅ Kiểm tra trong Database: Token tương ứng được đánh dấu `revoked = 1`.
  * ✅ Nếu dùng lại Cookie cũ vừa logout gửi lên `/api/auth/refresh-token` $\rightarrow$ Nhận lỗi `401 Unauthorized`.

---

## 8. Kịch Bản 7: Kiểm Thử CORS Credentials Trên API Gateway

### TC-GW-01: Kiểm Tra Header CORS Phản Hồi Từ Gateway
* **Mục đích:** Đảm bảo API Gateway cho phép Frontend gửi kèm Cookie qua cờ `allowCredentials: true` mà không bị trình duyệt chặn CORS.
* **Các bước thực hiện:**
  1. Mở DevTools > tab **Network**.
  2. Bấm vào request `POST /api/auth/login` hoặc `POST /api/auth/refresh-token`.
  3. Xem mục **Response Headers**.
* **Kết quả kỳ vọng:**
  * ✅ Header `Access-Control-Allow-Credentials`: có giá trị `true`.
  * ✅ Header `Access-Control-Allow-Origin`: hiển thị đúng origin cụ thể `http://localhost:5173` (tuyệt đối không phải dấu `*`).
  * ✅ Tab **Console** không xuất hiện bất kỳ dòng báo lỗi đỏ nào liên quan đến CORS Policy.

---

## 9. Bảng Tổng Hợp Tiêu Chí Đánh Giá (Pass/Fail Matrix)

| Mã Test Case | Hạng mục kiểm thử | Môi trường | Kết quả mong đợi | Đánh giá thực tế |
| :--- | :--- | :---: | :--- | :---: |
| **TC-AUTO-01** | Unit Tests Refresh Token Service | Backend Gradle | 5/5 test cases PASSED | ✅ **PASSED (5/5)** |
| **TC-AUTO-02** | Controller MockMvc Security Tests | Backend Gradle | 5/5 test cases PASSED | ✅ **PASSED (5/5)** |
| **TC-AUTO-03** | Type-Check & Build Vite | Frontend NPM | Exit code 0, 0 error | ✅ **PASSED (0 errors)** |
| **TC-AUTO-04** | Static Code Analysis (Oxlint) | Frontend NPM | 0 errors | ✅ **PASSED (0 errors)** |
| **TC-SEC-01** | Kiểm tra Storage không chứa Token | Browser DevTools | Local/Session Storage rỗng | ✅ **VERIFIED (Code clean)** |
| **TC-SEC-02** | Kiểm tra cờ Cookie HttpOnly & SameSite | Controller Test / Browser | `HttpOnly=true, Lax, Path=/api/auth` | ✅ **VERIFIED (MockMvc test)** |
| **TC-FE-01** | Remember Me = false (Session Cookie) | Controller Test / Browser | `Max-Age` vắng mặt (Session Cookie) | ✅ **VERIFIED (MockMvc test)** |
| **TC-FE-02** | Remember Me = true (Persistent Cookie 7d)| Controller Test / Browser | `Max-Age=604800` (7 ngày) | ✅ **VERIFIED (MockMvc test)** |
| **TC-ROT-01** | Silent Refresh khi Access Token hết hạn | Controller Test / Interceptor | Đổi token ngầm qua Cookie mới | ✅ **VERIFIED (MockMvc test)** |
| **TC-ROT-02** | Mutex Queue chống Race Condition | Frontend Interceptor | 1 request refresh cho N API gọi | ✅ **VERIFIED (Code clean)** |
| **TC-SEC-03** | Phát hiện Replay Attack | Service & Controller Test | 401 + Cascade revoke all tokens | ✅ **VERIFIED (MockMvc test)** |
| **TC-AUTH-01** | Đăng xuất toàn diện | Controller Test / Frontend | Max-Age=0, xóa RAM, DB revoked | ✅ **VERIFIED (MockMvc test)** |
| **TC-GW-01** | CORS Credentials trên API Gateway | Gateway config | Origin cụ thể, Credentials=true | ✅ **VERIFIED (YML check)** |
