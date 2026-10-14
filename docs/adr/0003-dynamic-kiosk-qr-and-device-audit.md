# ADR 0003: Tiến Hóa Kiến Trúc Chấm Công Cá Nhân (In-place Personal Dynamic QR, Device Binding & Trust-Level Audit)

- **Trạng thái:** ACCEPTED
- **Ngày quyết định:** 2026-10-11
- **Tác giả / Người quyết định:** Luong Anh Huy & Antigravity Pair-Programming
- **Phạm vi áp dụng:** `intern-and-program-service`, `InternHub-Frontend`, `API Gateway` và `internhub-redis-broker`.
- **Kế thừa & Bảo toàn:** Bảo toàn 100% luồng Personal Dynamic QR trên Dashboard theo [TM-25 Specification](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/TM-25-check-in-check-out-spec.md).

---

## 1. Ngữ cảnh & Vấn đề Cốt lõi (Context & Problem Statement)

1. **Bảo tồn tài sản mã nguồn & Trải nghiệm Dashboard:**
   - Hệ thống đã đầu tư hoàn chỉnh Widget Điểm danh trên Dashboard của từng Thực tập sinh (TTS) kèm Modal 3 khối và trang xác nhận di động (`/intern/attendance/confirm`).
   - Việc chuyển đổi sang Kiosk công cộng trung tâm sẽ đập bỏ toàn bộ luồng User-driven cá nhân và phát sinh chi phí thiết bị phần cứng.
   - Do đó, quyết định **tiến hóa tại chỗ (In-place Evolution)**: Giữ nguyên việc TTS chủ động bấm trên Dashboard để sinh Dynamic QR cá nhân (Personal QR), nhưng khắc phục triệt để các lỗ hổng gian lận.

2. **Chi tiết 7 Lỗ hổng & Vấn đề tồn đọng nghiêm trọng cần giải quyết:**

| # | Tên Vấn Đề | Thành Phần | Mức Độ | Ảnh Hưởng |
| :---: | :--- | :--- | :---: | :--- |
| **1** | Còn tồn tại endpoint legacy `POST /check-in` không cần QR | Backend Controller | 🔴 **CRITICAL** | Bypass hoàn toàn cơ chế bảo mật QR 60s qua API. |
| **2** | Nút "Mở link xác nhận" trực tiếp trên PC | Frontend Modal | 🔴 **CRITICAL** | Cho phép Fake GPS mà không cần cầm điện thoại thật. |
| **3** | Check-out không có cơ chế QR bảo vệ | Backend & FE | 🟠 **HIGH** | Có thể Fake GPS check-out từ xa vào cuối ngày. |
| **4** | Phiên QR lưu trên JVM RAM (`ConcurrentHashMap`) | Backend Service | 🟠 **HIGH** | Không hỗ trợ Multi-Instance / Cluster; mất token khi restart. |
| **5** | Điện thoại quét QR không gửi lại GPS của điện thoại | FE & BE Confirm | 🟡 **MEDIUM** | Nguy cơ chia sẻ ảnh QR qua mạng xã hội cho người ở nhà. |
| **6** | Bán kính 25m cứng, không tính sai số `accuracy` | Backend Calc | 🟡 **MEDIUM** | TTS ngồi trong văn phòng vẫn bị chặn do sóng GPS/Wi-Fi yếu. |
| **7** | Quên Check-out không có Auto-Close hoặc giải trình bù công | Nghiệp vụ EOD | 🟡 **MEDIUM** | Mất dữ liệu giờ công thực tế của TTS. |

---

## 2. Quyết định Kiến trúc: Mô Hình "Trust Level Engine" Tại Backend

Hệ thống quyết định áp dụng mô hình **Đánh Giá Độ Tin Cậy Đa Tầng (Multi-Factor Trust Engine)** tại Backend, kết hợp **Định vị Kép (IP/Wi-Fi Subnet + Optional GPS)** và **Cơ chế Check-Out Thích Ứng (Adaptive Trust-Level Check-Out)**:

```
┌─────────────────────────────────────────────────────────────────┐
│     THỰC TẬP SINH BẤM "VÀO CA" TRÊN LAPTOP (DASHBOARD)          │
│  - Sinh Personal Dynamic QR (TTL 60s, gắn chặt internId)        │
│  - Lưu phiên vào Redis Broker (Cluster-ready)                   │
└────────────────────────────────┬────────────────────────────────┘
                                 │ (Quét mã bằng Camera Điện Thoại)
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│              TRANG XÁC NHẬN TRÊN MOBILE (CONFIRM PAGE)          │
│  - Thu thập: Device Fingerprint + Optional GPS + Client IP/Wi-Fi│
│  - Gửi POST /api/v1/attendances/check-in/confirm                │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│           BACKEND: ĐÁNH GIÁ ĐỘ TIN CẬY (TRUST LEVEL ENGINE)     │
│                                                                 │
│  [Yếu tố 1: QR Token hợp lệ & chưa hết hạn (Bắt buộc)]         │
│  [Yếu tố 2: Mạng văn phòng (IP khớp organization_settings?)]   │
│  [Yếu tố 3: Thiết bị di động (Trùng Primary Device?)]           │
│  [Yếu tố 4: GPS Geofencing (Khoảng cách <= 50m?)]               │
│                                                                 │
│  ┌─────────────────────────────┬──────────────────────────────┐ │
│  │ TỔ HỢP TÍN HIỆU             │ KẾT QUẢ ĐÁNH GIÁ & GHI NHẬN  │ │
│  ├─────────────────────────────┼──────────────────────────────┤ │
│  │ Đúng máy + IP cty + GPS ≤50m│ HIGH -> APPROVED             │ │
│  │ Đúng máy + IP cty, ko GPS   │ MEDIUM -> APPROVED (NO_GPS)  │ │
│  │ Đúng máy + GPS ≤50m (dùng 4G│ MEDIUM -> APPROVED           │ │
│  │  ko kết nối Wi-Fi công ty)  │ (CELLULAR_DATA_OK)           │ │
│  │ Máy lạ + Có mặt tại cty     │ MEDIUM -> APPROVED           │ │
│  │  (khớp IP hoặc GPS ≤ 50m)   │ + Gắn cờ NEW_DEVICE          │ │
│  │ (MÁY LẠ) VÀ (CẢ IP & GPS    │ LOW -> PENDING_REVIEW        │ │
│  │  ĐỀU NGOÀI CÔNG TY)         │ + Cờ SUSPICIOUS_FRAUD_RISK   │ │
│  └─────────────────────────────┴──────────────────────────────┘ │
│                                                                 │
│  Ghi Append-Only vào `attendance_audit_logs`                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Chi Tiết Các Quy Tắc Nghiệp Vụ & Trải Nghiệm (UX Design)

### 3.1. Định Vị Kép: Network Anchor (IP/Wi-Fi) + Optional GPS
- **Cấu hình động qua `organization_settings` (Chốt dứt điểm):** Dải IP văn phòng và toạ độ công ty được cấu hình động trong bảng `organization_settings` (quản trị viên/HR cập nhật qua UI Admin mà không cần build hay redeploy ứng dụng khi đổi nhà mạng hoặc thêm văn phòng).
- **Phối hợp tín hiệu mạng & GPS:**
  - **Case dùng 4G tại văn phòng:** TTS ngồi tại công ty nhưng quên bật Wi-Fi, dùng mạng 4G/5G cá nhân. Toạ độ GPS phản ánh chính xác $\le 50\text{m}$ $\rightarrow$ Vẫn công nhận hợp lệ (`MEDIUM_TRUST`), không ép buộc phải có Wi-Fi.
  - **Case phòng họp kín:** Mất sóng GPS nhưng laptop và điện thoại chung IP văn phòng $\rightarrow$ Vẫn công nhận hợp lệ (`MEDIUM_TRUST`).
  - **Quy tắc AND chống gian lận kép:** Hệ thống chỉ đánh tụt xuống `LOW_TRUST` khi xuất hiện đồng thời cả 2 tín hiệu nghi ngờ: **Vừa dùng thiết bị lạ, vừa không có bất kỳ bằng chứng vị trí nào (cả IP và GPS đều nằm ngoài công ty)**.

### 3.2. Trải Nghiệm Check-Out Thích Ứng (Adaptive Check-Out UX)
Giải quyết bài toán TTS đã gập laptop, cất vào balo khi tan ca:
- **Tín hiệu tin cậy cao (`HIGH_TRUST` / `MEDIUM_TRUST`):** Điện thoại đang kết nối Wi-Fi công ty hoặc GPS $\le 50\text{m}$ + đúng thiết bị đã đăng ký $\rightarrow$ Cho phép **Check-out 1 chạm ngay trên điện thoại (Mobile Quick Check-Out)** mà không cần bắt buộc phải lôi laptop ra quét lại QR.
- **Tín hiệu nghi vấn / Rủi ro (`LOW_TRUST`):** Không cho phép Check-out 1 chạm từ xa. Bắt buộc quay lại quy trình quét QR trên màn hình laptop cá nhân để xác thực vật lý on-site.

### 3.3. Phân Tầng Độ Tin Cậy (`TrustLevel`) & Trạng Thái Bản Ghi
- **`HIGH_TRUST` (Độ tin cậy cao):** Đúng máy đã đăng ký + IP công ty + GPS trong bán kính văn phòng $\le 50\text{m}$.
  - Trạng thái công: Ghi nhận hợp lệ ngay lập tức (`ON_TIME` hoặc `LATE`), `review_status = AUTO_APPROVED`.
  - Giao diện TTS: Màn hình xanh chúc mừng thành công.
- **`MEDIUM_TRUST` (Độ tin cậy tiêu chuẩn - Chấp nhận sai số ngoại cảnh):**
  - Nhánh 1: Đúng máy + IP công ty nhưng GPS yếu / không lấy được GPS $\rightarrow$ Cờ `NO_GPS_SUPPLIED`.
  - Nhánh 2: Đúng máy + GPS $\le 50\text{m}$ nhưng dùng mạng 4G/5G ngoài dải IP công ty $\rightarrow$ Cờ `CELLULAR_DATA_OK`.
  - Nhánh 3: **Đổi máy / Máy lạ nhưng đang ngồi tại công ty** (khớp IP văn phòng hoặc GPS $\le 50\text{m}$) $\rightarrow$ Ghi nhận hợp lệ ngay (`AUTO_APPROVED`), tự động gắn cờ `NEW_DEVICE` để HR theo dõi mà **không bắt người dùng chờ duyệt**.
- **`LOW_TRUST` (Độ tin cậy thấp - Nguy cơ gian lận từ xa):** Thỏa mãn điều kiện kép: **(Thiết bị mới/lạ) VÀ (Cả IP và GPS đều nằm ngoài công ty)**.
  - **Trạng thái công:** Ghi nhận vào hệ thống nhưng đặt trạng thái kiểm duyệt là **`PENDING_REVIEW`**.
  - **Cờ kiểm toán:** `SUSPICIOUS_FRAUD_RISK` (`NEW_DEVICE` + `GEO_OUT_OF_BOUNDS`).
  - **UI phụ dành riêng cho TTS:** Hiển thị thẻ thông báo màu vàng thân thiện:
    > *"Điểm danh đã được ghi nhận và đang ở trạng thái **Chờ HR xác nhận** do bạn thực hiện từ một thiết bị mới ngoài mạng văn phòng. Ngày công của bạn sẽ được giữ nguyên sau khi HR đối soát."*

### 3.4. Chuẩn Bị Schema Cho Phân Hệ HR Duyệt (Schema Ready, UI Later)
Để tránh phải can thiệp DB production nhiều lần, đợt này bổ sung sẵn 4 trường kiểm duyệt vào bảng `attendances`:
- `review_status`: `VARCHAR(32)` (`AUTO_APPROVED`, `PENDING_REVIEW`, `HR_APPROVED`, `HR_REJECTED`).
- `reviewed_by`: `VARCHAR(64)` (ID của HR/Mentor thực hiện duyệt).
- `reviewed_at`: `DATETIME` (Thời điểm xử lý phê duyệt).
- `review_note`: `TEXT` (Lý do chấp thuận hoặc từ chối công).
- **Giao diện hiện tại:** Thêm 1 badge hiển thị tối thiểu trên bảng lịch sử chấm công (Vàng: `Chờ duyệt`, Xanh: `Đã duyệt`). Giao diện Admin/HR thao tác duyệt hàng loạt sẽ được triển khai ở User Story chuyên biệt tiếp theo.

### 3.5. Đóng Chặt Các Lỗ Hổng Bảo Mật (Security Hardening)
1. **Khóa vĩnh viễn endpoint bypass:** Xóa bỏ controller method `POST /api/v1/attendances/check-in` (direct check-in). Bắt buộc 100% request phải đi qua cặp API `/check-in/initiate` $\rightarrow$ `/check-in/confirm`.
2. **Ẩn nút bypass trên PC:** Loại bỏ nút "Mở link xác nhận" và "Sao chép link" trên Modal laptop ở chế độ Production. Bắt buộc TTS phải dùng Camera điện thoại quét hình ảnh thực tế trên màn hình.
3. **Quét mã ngắn hạn & Token bảo mật:** QR token xoay vòng trong 60s trên Redis, 1 lần sử dụng (One-time Nonce).

### 3.6. Tự Động Đóng Ca Cuối Ngày (EOD Auto-Close Job)
- Job tự động chạy lúc 23:59 hàng ngày: Quét các ca có `check_in_time` nhưng thiếu `check_out_time`.
- Đánh dấu trạng thái ca là `FORGOTTEN_CHECKOUT` với cờ `MISSING_CHECKOUT_FLAG`. TTS có thể nộp đơn giải trình bổ sung giờ ra về vào hôm sau.

---

## 4. Đánh Đổi & Hệ Quả (Consequences & Trade-offs)

### Mặt tích cực:
- **Bảo toàn 100% kiến trúc & code hiện có:** Không lãng phí công sức đã làm cho Dashboard, giữ trọn vẹn thói quen sử dụng của TTS.
- **Trải nghiệm mượt mà, không làm phiền người dùng hợp lệ:** TTS đổi điện thoại hoặc dùng mạng 4G tại văn phòng vẫn được chấm công bình thường, không bị đẩy oan vào hàng đợi duyệt của HR.
- **Trải nghiệm tan ca mượt mà (Smooth Checkout):** TTS không bị ức chế vì phải lôi laptop ra quét mã QR khi đã cất đồ ra về nếu vẫn ở trong khuôn viên văn phòng.
- **Sẵn sàng tương lai (Future-Proof):** Schema DB đã có sẵn các trường Review cho HR mà không làm phình to scope UI của đợt này.

### Mặt đánh đổi & Hạn chế đã biết (Trade-offs & Known Limitations):
- **Phụ thuộc vào 2 thiết bị cá nhân của TTS:** Kiến trúc Personal QR vẫn dựa trên 2 thiết bị do TTS làm chủ (laptop + điện thoại). Do đó, hệ thống **không thể loại bỏ 100%** khả năng gian lận có chủ đích cao (ví dụ: cài ứng dụng Fake GPS/Mock Location trên điện thoại kết hợp dùng VPN nội bộ giả lập IP văn phòng). Rủi ro này được chủ động chấp nhận và giảm thiểu bằng Trust Level Engine cùng Audit Trail hậu kiểm thay vì cấm đoán cực đoan.
- **Độ tin cậy của Network Subnet:** Company IP Subnet là lớp phòng thủ tương đối, không phải bằng chứng vị trí tuyệt đối (IP có thể bị ảnh hưởng nếu cấu hình VPN công ty cho phép split-tunneling hoặc remote-access từ xa).
- **Trách nhiệm hậu kiểm của HR:** Chế độ Advisory chuyển một phần áp lực từ việc chặn cứng sang việc đối soát bất thường cuối kỳ của bộ phận nhân sự.

---

## 5. Kế Hoạch Triển Khai Kỹ Thuật (Implementation Checklist)
1. **Backend (`intern-and-program-service`):**
   - Flyway Migration / DDL: Thêm 4 cột `review_status`, `reviewed_by`, `reviewed_at`, `review_note` vào `attendances`.
   - Tạo Entity & Repository: `AttendanceAuditLog` và `InternDevice`.
   - Đọc dải Company IP Subnet động từ `organization_settings`.
   - Nâng cấp `TrustLevelEvaluator`: Tính toán điểm tin cậy dựa trên Thiết bị + IP Mạng + GPS với đúng logic AND/OR đã chốt.
   - Endpoint mới: `POST /api/v1/attendances/check-out/quick` (Dành cho Mobile khi thỏa mãn High/Medium Trust).
   - Xóa bỏ endpoint legacy `POST /api/v1/attendances/check-in`.
   - Nâng cấp `CheckInQrTokenManager` lưu phiên qua Redis.
   - Bổ sung Spring Scheduled Task (23:59 EOD Auto-Close).
2. **Frontend (`InternHub-Frontend`):**
   - Ẩn nút "Mở link xác nhận" trên `AttendanceActionModal.tsx`.
   - Nâng cấp `InternAttendanceConfirmPage.tsx`: Thu thập Device Fingerprint + Optional GPS + hiển thị UI màu vàng khi `PENDING_REVIEW`.
   - Thêm nút "Check-out nhanh" trên giao diện Mobile nếu hệ thống phát hiện đủ điều kiện trust.
   - Hiển thị Badge trạng thái kiểm duyệt tối thiểu (`review_status`) trên bảng lịch sử chấm công.
