# Danh Sách Nhiệm Vụ Triển Khai (Todo List)
## Nâng Cấp Hệ Thống Chấm Công Chuyên Cần v2.0 (Dynamic QR, Device Binding, SSE Real-time & Adaptive Check-out)

> **Kế hoạch tổng thể:** [tasks/plan.md](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/tasks/plan.md)  
> **Nguyên tắc:** Làm đến đâu kiểm thử đến đó, không làm tắt, không phá vỡ tính năng hiện có.

---

### Phase 1: Database Migration & Foundation Entities
- [x] **Task 1.1:** Cập nhật schema DDL và JPA Mapping (Thêm 4 cột review vào `attendances`, tạo bảng `attendance_audit_logs`, tạo bảng `intern_devices`).
- [x] **Task 1.2:** Mapping JPA Entities & Repositories (`Attendance`, `AttendanceAuditLog`, `InternDevice`, Enums: `AttendanceReviewStatus`, `TrustLevel`, `DeviceTrustStatus`).

### Checkpoint 1: DB & Entities
- [x] Chạy migration và compileJava thành công trên MySQL, entity mappings biên dịch pass 100%.

---

### Phase 2: Core Backend - Trust Engine, Redis & Network Subnet
- [x] **Task 2.1:** Tạo tiện ích `IpSubnetMatcher` và cơ chế đọc dải IP văn phòng động từ `office_locations` (hỗ trợ CIDR & single IP).
- [x] **Task 2.2:** Di chuyển `CheckInQrTokenManager` sang Redis Broker (TTL 60s, Grace skew 15s, One-time Nonce chống Replay, in-memory fallback).
- [x] **Task 2.3:** Phát triển `TrustLevelEvaluator` với ma trận AND/OR hoàn chỉnh (High, Medium, Low trust, bù trừ `accuracy`).

### Checkpoint 2: Core Logic Tests
- [x] Unit Test `TrustLevelEvaluatorTest` vượt qua 100% các case trong ma trận.
- [x] Redis token manager lưu và hủy token chính xác.

---

### Phase 3: API Gateway, Realtime SSE Stream & Controller Endpoints
- [x] **Task 3.1:** Xây dựng SSE Stream Endpoint `GET /api/v1/attendances/check-in/stream` và `SseEmitterRegistry`.
- [x] **Task 3.2:** Nâng cấp Endpoint Confirm `POST /check-in/confirm` tích hợp Trust Engine, Audit Log, và bắn sự kiện SSE.
- [x] **Task 3.3:** Viết Endpoint mới `POST /check-out/quick` (1-chạm di động) và xóa bỏ direct bypass `POST /check-in`.
- [x] **Task 3.4:** Cấu hình API Gateway hỗ trợ SSE streaming không bị ngắt timeout.

### Checkpoint 3: Backend API Endpoints
- [x] Backend compile pass, toàn bộ test suite Attendance pass 100%, sẵn sàng cho SSE stream và confirm.

---

### Phase 4: Frontend - Trải Nghiệm Laptop Modal UX
- [x] **Task 4.1:** Nâng cấp `AttendanceActionModal.tsx`: Đồng hồ đếm ngược SVG Radial 60s + Silent Prefetch khi còn 5s + Auto-pause 3 phút.
- [x] **Task 4.2:** Tích hợp `EventSource` (SSE Client) trên Modal: Tự động chúc mừng xanh lá khi điện thoại quét xong, tự đóng sau 3s và reload card Dashboard.
- [x] **Task 4.3:** Ẩn nút "Mở link xác nhận" trên Production và bổ sung micro-guide hoạt họa.

---

### Phase 5: Frontend - Mobile Experience & Adaptive Check-out
- [x] **Task 5.1:** Nâng cấp `InternAttendanceConfirmPage.tsx`: Thu thập Device Fingerprint + Pre-permission banner GPS + Thẻ trạng thái minh bạch (Xanh / Vàng `PENDING_REVIEW`).
- [x] **Task 5.2:** Bổ sung giao diện "Tan Ca Nhanh 1-Chạm" (`POST /check-out/quick`) trên Web Mobile khi thỏa mãn điều kiện trust.
- [x] **Task 5.3:** Thêm Badge trạng thái kiểm duyệt (`Chờ duyệt` / `Đã duyệt`) trên bảng lịch sử chấm công.

---

### Phase 6: Nghiệp Vụ EOD & Kiểm Thử Toàn Diện
- [x] **Task 6.1:** Tạo Spring Scheduled Task 23:59 EOD Auto-Close cho các ca quên check-out (`FORGOTTEN_CHECKOUT`).
- [x] **Task 6.2:** Chạy toàn bộ Test Suite của `intern-and-program-service` (BUILD SUCCESSFUL 100%).
- [x] **Task 6.3:** Smoke Test E2E thực tế trên Frontend build và Backend endpoints.
