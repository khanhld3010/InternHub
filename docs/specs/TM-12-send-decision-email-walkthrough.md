# Walkthrough: TM-12 Gửi Email Thông Báo Kết Quả Xét Duyệt Hồ Sơ Thực Tập Sinh

Chúng ta đã hoàn thành xuất sắc toàn bộ tính năng **TM-12** theo đúng tiêu chuẩn kiến trúc Microservice và quy chuẩn Production-grade.

---

## 1. Tóm Tắt Các Thành Phần Đã Triển Khai

### 1.1. Module Gửi Mail: `reporting-and-integration-service` (Port 8083)
- **Spring Mail & Template HTML Responsive:**
  - Tích hợp `spring-boot-starter-mail`.
  - Triển khai `EmailTemplateBuilder` tạo layout HTML đẹp mắt, chuẩn 100% theo mẫu thiết kế:
    - **Email Approved:** Header tím `#4f46e5`, badge `✓ HỒ SƠ ĐÃ ĐƯỢC DUYỆT`, greeting chúc mừng, card chi tiết vị trí/phòng ban/mentor/ngày bắt đầu, nút CTA `Xem thông tin onboarding →` trỏ tới `${APP_FRONTEND_URL}/onboarding/activate?token=...`.
    - **Email Rejected:** Header slate dark `#1e293b`, thư cảm ơn lịch sự, box nổi bật hiển thị rõ ràng lý do từ chối cụ thể `rejectionReason` do HR nhập ở TM-11.
- **Xử lý Bất đồng bộ & Lưu vết Lịch sử:**
  - `EmailDeliveryService` gửi email ngầm qua `@Async`, lưu nhật ký vào bảng `email_logs` (kèm `idempotencyKey`, `status`, `errorMessage`, `sentAt`).
  - Hỗ trợ chế độ `mockMode: true` an toàn trong môi trường dev/test khi chưa cấu hình mật khẩu SMTP thật.
  - Sau khi gửi xong, gọi REST callback sang `intern-and-program-service` để cập nhật trạng thái hồ sơ.
- **REST Endpoints Nội Bộ:**
  - `POST /api/integration/emails/intern-decision`: Tiếp nhận yêu cầu gửi mail, kiểm tra Idempotency chống gửi lặp.
  - `GET /api/integration/emails/status/{internProfileId}`: Tra cứu trạng thái email gần nhất của hồ sơ.

---

### 1.2. Core Domain: `intern-and-program-service` (Port 8082)
- **Token Kích Hoạt & An Toàn Safe Links Scanner:**
  - `OnboardingTokenService`: Sinh JWT Token có thời hạn 7 ngày, chứa claims `internId`, `email`, `fullName`, `type: ONBOARDING_ACTIVATION`.
  - `OnboardingController`:
    - `GET /api/onboarding/verify-token`: Safe Links Scanner của Outlook 365 / Google Workspace quét qua chỉ thực hiện đọc kiểm tra, **không làm mất hiệu lực token**.
    - `POST /api/onboarding/activate`: Nhận mật khẩu mới, kích hoạt tài khoản thành công.
- **Bảo Vệ Rate Limit 2 Lớp (Server-side):**
  - **Cooldown 45 giây:** Nếu gửi lại trong vòng 45s, trả về `HTTP 429 Too Many Requests` kèm `Retry-After` header và body `{ retryAfter: seconds }`.
  - **Giới hạn 5 lần/ngày/hồ sơ:** Ngăn chặn spam hoặc spam bounce do email ứng viên bị sai.
- **Khả Năng Phục Hồi (Reconciliation):**
  - `EmailReconciliationJob`: Cron job định kỳ mỗi 5 phút quét và giải tỏa các hồ sơ bị treo `emailStatus = PENDING > 5 phút` sang `FAILED`.
- **API Mới:**
  - `POST /api/interns/{id}/resend-decision-email`: Cho phép HR gửi lại email khi gặp lỗi (có phân quyền `ROLE_HR`, `ROLE_ADMIN`).
  - `PATCH /api/interns/{id}/email-status`: Endpoint nội bộ cho callback từ reporting service.

---

### 1.3. Giao Diện Người Dùng: `InternHub-Frontend`
- **Tích Hợp Tinh Gọn Tại Header `DetailInternModal`:**
  - Không tạo màn hình rườm rà. Dòng meta-info đặt ngay dưới badge trạng thái ở header hồ sơ:
    - *Đã gửi thành công:* `✓ Đã gửi email thông báo · dd/MM/yyyy HH:mm` (màu `--text-muted`).
    - *Đang gửi:* `⏳ Đang gửi email...`.
    - *Thất bại:* `✗ Gửi email thất bại` kèm nút text `[Gửi lại]`.
  - Khi bấm "Gửi lại" hoặc nhận mã `429`, nút chuyển sang đếm ngược cooldown tick từng giây: `(Gửi lại sau 45s)`.
- **Trang Candidate Onboarding Activation:**
  - Route public `/onboarding/activate?token=...`:
    - Tự động gọi verify token khi load trang.
    - Hiển thị form thiết lập mật khẩu mới (tối thiểu 6 ký tự).
    - Thông báo kích hoạt thành công và điều hướng vào trang đăng nhập.

---

## 2. Kết Quả Kiểm Thử (Verification Results)

| Thành phần | Loại kiểm thử | Kết quả |
| :--- | :--- | :--- |
| `reporting-and-integration-service` | Unit Test `EmailDeliveryServiceTest` | **5/5 tasks passed** (100% success) |
| `intern-and-program-service` | Unit Test `InternProfileServiceTest` | **25/25 tests passed** (Bao gồm Cooldown 45s & Max 5 retries/day) |
| `InternHub-Frontend` | Production Bundle Build (`tsc -b && vite build`) | **Build thành công không lỗi TypeScript** |

---

## 3. Các Branch Đã Cập Nhật
- Backend repo (`InternHub`): `feature/TM-12/send-decision-email`
- Frontend repo (`InternHub-Frontend`): `feature/TM-12/send-decision-email`
