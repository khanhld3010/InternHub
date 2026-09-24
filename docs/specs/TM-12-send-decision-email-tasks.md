# Task Breakdown: TM-12 Gửi Email Thông Báo Kết Quả Xét Duyệt Hồ Sơ

---

## Phase 1: Branch Setup & Environment
- [ ] **Task 1.1:** Tạo branch `feature/TM-12/send-decision-email` trên cả hai repo `InternHub` và `InternHub-Frontend`.

---

## Phase 2: Backend `reporting-and-integration-service` (Port 8083)
- [ ] **Task 2.1:** Thêm dependency `spring-boot-starter-mail` vào `build.gradle` của `reporting-and-integration-service`.
- [ ] **Task 2.2:** Cấu hình SMTP properties & Async Executor trong `reporting-and-integration-service.yml` và Config class.
- [ ] **Task 2.3:** Tạo Entity `EmailLog` và `EmailLogRepository` để lưu vết lịch sử gửi email.
- [ ] **Task 2.4:** Xây dựng HTML Email Template cho hồ sơ `APPROVED` (chúc mừng, mã TTS, vị trí, ngày bắt đầu) và `REJECTED` (thông báo lịch sự, lý do từ chối cụ thể).
- [ ] **Task 2.5:** Viết `EmailService` xử lý gửi mail bất đồng bộ `@Async` và lưu trạng thái `EmailLog`.
- [ ] **Task 2.6:** Xây dựng REST API nội bộ `POST /api/integration/emails/intern-decision` và `GET /api/integration/emails/status/{internProfileId}`.
- [ ] **Task 2.7:** Viết Unit Test cho `EmailService` và Controller.

---

## Phase 3: Backend `intern-and-program-service` (Port 8082)
- [ ] **Task 3.1:** Thêm trường `emailStatus` và `emailSentAt` vào entity `InternProfile` và DTO `InternResponse`.
- [ ] **Task 3.2:** Tạo `IntegrationEmailClient` (OpenFeign hoặc WebClient/RestTemplate qua Eureka) kết nối sang `reporting-and-integration-service`.
- [ ] **Task 3.3:** Tạo `InternDecisionEventListener` bắt `InternDecisionProcessedEvent` và gọi `IntegrationEmailClient` bất đồng bộ.
- [ ] **Task 3.4:** Thêm endpoint `POST /api/interns/{id}/resend-decision-email` cho phép HR/Admin gửi lại email nếu trạng thái trước đó bị `FAILED`.
- [ ] **Task 3.5:** Cập nhật Unit Test trong `InternProfileServiceTest` và `InternProfileControllerTest`.

---

## Phase 4: Frontend `InternHub-Frontend`
- [ ] **Task 4.1:** Cập nhật TypeScript types trong `src/types/intern.ts` bổ sung `emailStatus` và `emailSentAt`.
- [ ] **Task 4.2:** Thêm hàm `resendDecisionEmail(id)` vào `src/services/internService.ts`.
- [ ] **Task 4.3:** Cập nhật UI trong `DetailInternModal.tsx`:
  - Hiển thị dòng trạng thái nhỏ dưới badge trạng thái ở header modal:
    - `✓ Đã gửi email thông báo · dd/MM/yyyy HH:mm`
    - `⏳ Đang gửi email...`
    - `✗ Gửi email thất bại` + inline button `[Gửi lại]`
  - Xử lý click nút "Gửi lại": gọi API, hiển thị loading indicator và toast thông báo.

---

## Phase 5: Verification & Review
- [ ] **Task 5.1:** Chạy test toàn bộ backend (`./gradlew test`).
- [ ] **Task 5.2:** Kiểm tra lint / build frontend (`npm run build`).
- [ ] **Task 5.3:** Tạo tài liệu `TM-12-send-decision-email-walkthrough.md` tổng kết kết quả.
