# Specification: Chức Năng Chấm Công Check-in / Check-out Cho Thực Tập Sinh (Intern Attendance Check-in / Check-out)

> **Trạng thái:** IMPLEMENTED  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-25-check-in-check-out-spec.md`  
> **Áp dụng quy tắc:** [Persistent Spec & Change Rationale](file:///d:/Certificate_CodeGym/Module%206/InternHub/.agents/04-development-guide.md)

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

> [!IMPORTANT]
> **BẮT BUỘC ĐIỀN ĐẦY ĐỦ**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic, API, validation hay database (từ cấp độ L2 trở lên), **bắt buộc** phải ghi thêm một dòng vào bảng này để giải trình lý do trước khi coi nhiệm vụ là hoàn tất.

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0** | 2026-09-29 | AI Pair-Programmer | `TM-25` | Tạo mới | Thiết kế đặc tả ban đầu cho tính năng chấm công check-in / check-out của thực tập sinh với xác thực định vị GPS trong bán kính 25m, giờ chuẩn 08:00 - 17:30, ngưỡng muộn 08:15. |
| **v1.1** | 2026-09-30 | AI Pair-Programmer | `TM-25` | Cấu hình & Tọa độ | Bổ sung route `attendance-service` trên API Gateway khắc phục lỗi 404 và cập nhật tọa độ văn phòng mặc định sang (21.035665, 105.768296) theo địa điểm làm việc thực tế. |
| **v1.2** | 2026-10-01 | AI Pair-Programmer | `TM-25` | Logic Xác Thực & API | Bổ sung lớp xác thực 2 bước bằng mã QR: Sau khi kiểm tra GPS hợp lệ, sinh mã QR chứa token phiên (hiệu lực 60s); xác thực thành công mới cho phép hoàn tất ghi nhận Check-in. |
| **v1.3** | 2026-10-01 | AI Pair-Programmer | `TM-25` | QR Payload & URL Resolution | Chuyển đổi payload mã QR từ token thô sang Actionable Confirmation URL (`/intern/attendance/confirm?token=UUID`), bổ sung `clientBaseUrl` và cơ chế phân giải 3 tầng (Request body -> Origin/Referer header -> Default) hỗ trợ cả Browser Extension và camera điện thoại thật qua mạng Wi-Fi LAN. |
| **v2.0** | 2026-10-12 | Luong Anh Huy & AI | `TM-25` | Nâng Cấp Toàn Diện (In-place Evolution) | Tái thiết kế trải nghiệm 12 điểm doanh nghiệp: Multi-factor Trust Level Engine (Device + IP Subnet + GPS), Real-time SSE Sync (Phone -> Laptop), Check-out thích ứng 1-chạm, EOD Auto-close 23:59, Xóa bỏ endpoint legacy bypass và schema sẵn sàng cho HR review theo ADR 0003. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)
- **Feature Name:** Chấm công Check-in / Check-out cho Thực tập sinh (Intern Attendance Check-in / Check-out)
- **Jira Ticket:** [TM-25](https://robluccibn9935.atlassian.net/browse/TM-25)
- **Target Microservice:** `intern-and-program-service` (Port: 8082)
- **Target Users & Roles:** `INTERN` (người thực hiện chấm công), `HR` / `ADMIN` (xem dữ liệu và cấu hình tọa độ văn phòng)
- **Change Level:** **L3** (Tính năng mới, tác động Cơ sở dữ liệu, API mở rộng và xác thực nghiệp vụ không gian địa lý GPS)

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)
1. **Ghi nhận thời gian làm việc thực tế:** Tự động hóa quá trình ghi nhận thời gian vào làm và ra về hàng ngày của thực tập sinh trên hệ thống InternHub.
2. **Xác thực vị trí hiện diện thực tế (Chống gian lận chấm công):** Giới hạn việc chấm công chỉ được thực hiện khi thiết bị của thực tập sinh nằm trong bán kính quy định ($\le 25\text{m}$) so với tọa độ văn phòng công ty.
3. **Đánh giá tính chuyên cần minh bạch:** Tự động phân loại trạng thái chuyên cần (Đúng giờ, Đi muộn, Về sớm) dựa trên khung giờ chuẩn `08:00 - 17:30` và mốc tính muộn sau `08:15`.
4. **Tối ưu trải nghiệm thực tập sinh:** Cung cấp thông tin trạng thái ngày làm việc tức thì ngay trên Widget Dashboard (`intern/dashboard`).

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- **API Check-in:** Nhận tọa độ GPS `(latitude, longitude)`, kiểm tra khoảng cách với văn phòng bằng công thức Haversine ($\le 25\text{m}$), kiểm tra tính hợp lệ về ngày và trạng thái, ghi nhận giờ check-in và tính trạng thái (`ON_TIME` hoặc `LATE`).
- **API Check-out:** Nhận tọa độ GPS, xác thực khoảng cách $\le 25\text{m}$, ghi nhận giờ check-out, tính tổng số giờ làm việc trong ngày (`totalWorkingHours`), cập nhật trạng thái nếu về sớm trước `17:30` (`EARLY_LEAVE` hoặc `LATE_AND_EARLY_LEAVE`).
- **API Lấy trạng thái hôm nay (`today`):** Trả về trạng thái ngày làm việc hiện tại của Intern đang đăng nhập (chưa check-in, đã check-in nhưng chưa check-out, hoặc đã hoàn thành).
- **API Lịch sử chấm công cá nhân (`my-history`):** Truy vấn danh sách bản ghi chấm công theo tháng/năm của Intern hiện tại.
- **Bảng cấu hình tọa độ văn phòng (`office_locations`):** Lưu trữ tọa độ văn phòng gốc, bán kính cho phép (mặc định 25m) và hỗ trợ Admin cập nhật tọa độ thực sau này mà không cần sửa code.

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn chặn suy diễn sai*)
- **Tuyệt đối KHÔNG triển khai:**
  - Quy trình Mentor / HR chỉnh sửa hoặc phê duyệt bù công bằng tay (tạm hoãn theo yêu cầu).
  - Quy trình gửi đơn xin nghỉ phép / giải trình đi muộn (`AttendanceRequest`).
  - Chấm công nhiều ca trong ngày (chỉ cho phép tối đa 1 lượt Check-in và 1 lượt Check-out mỗi ngày).
  - Thay đổi mã nguồn Frontend (tuân thủ ranh giới cách ly Trụ cột II).

---

## 4. Potential Logic Loopholes & Mitigations (Tối thiểu 5 Edge Cases Cốt Lõi)

### 4.1. Case 1: Gian lận hoặc sai lệch tọa độ GPS (Ngoài bán kính 25m)
- **Vấn đề:** Intern bấm check-in từ xa (ở nhà hoặc quán cà phê ngoài bán kính 25m) hoặc thiết bị gửi tọa độ không hợp lệ.
- **Giải pháp:** Backend sử dụng công thức Haversine tính khoảng cách giữa tọa độ client gửi lên và tọa độ văn phòng đang kích hoạt trong bảng `office_locations`. Nếu khoảng cách $> 25.0\text{m}$, lập tức chặn và trả về HTTP `400 Bad Request` kèm thông báo: *"Vị trí của bạn cách văn phòng [X]m, vượt quá bán kính cho phép (25m)"*.

### 4.2. Case 2: Double-submit Check-in / Concurrency Check-in
- **Vấn đề:** Intern bấm nút Check-in liên tục nhiều lần cùng một lúc hoặc hai tab trình duyệt gửi đồng thời, dẫn đến tạo 2 bản ghi cho cùng một ngày.
- **Giải pháp:** 
  1. Đặt ràng buộc duy nhất trên Database: `UNIQUE KEY uk_intern_work_date (intern_id, work_date)`.
  2. Tại Service: Kiểm tra `attendanceRepository.findByInternIdAndWorkDate(internId, today)`. Nếu đã tồn tại, ném `DuplicateResourceException` trả về HTTP `409 Conflict` với thông báo *"Bạn đã thực hiện check-in cho ngày hôm nay rồi"*.

### 4.3. Case 3: Check-out khi chưa Check-in
- **Vấn đề:** Intern bấm Check-out trong khi chưa từng bấm Check-in trong ngày làm việc hôm đó.
- **Giải pháp:** Kiểm tra bản ghi của ngày hiện tại. Nếu chưa tồn tại bản ghi hoặc `checkInTime == null`, ném `BadRequestException` với thông báo: *"Bạn chưa thực hiện check-in cho ngày hôm nay, không thể check-out"*.

### 4.4. Case 4: Check-out nhiều lần trong ngày
- **Vấn đề:** Intern đã check-out hoàn tất nhưng lại bấm nút Check-out một lần nữa trong cùng ngày.
- **Giải pháp:** Kiểm tra trường `checkOutTime` của bản ghi hôm đó. Nếu đã có dữ liệu (`checkOutTime != null`), ném `BadRequestException` với thông báo: *"Bạn đã hoàn thành check-out cho ngày hôm nay rồi"*.

### 4.5. Case 5: Lệch múi giờ giữa Trình duyệt (Client) và Server
- **Vấn đề:** Trình duyệt gửi thời gian local của máy tính bị sai lệch (ví dụ chỉnh giờ hệ thống về trước 08:15 để né phạt muộn).
- **Giải pháp:** **Tuyệt đối không tin cậy thời gian do Client gửi lên**. Client chỉ gửi tọa độ `latitude`, `longitude`. Mọi mốc thời gian `work_date`, `check_in_time`, `check_out_time` đều do Backend lấy trực tiếp từ đồng hồ Server chuẩn hóa theo múi giờ `ZoneId.of("Asia/Ho_Chi_Minh")` (GMT+7).

### 4.6. Case 6: Kiểm tra trạng thái hồ sơ thực tập sinh
- **Vấn đề:** Tài khoản người dùng đã bị đình chỉ (`SUSPENDED`), đã tốt nghiệp (`COMPLETED`) hoặc chưa onboard nhưng vẫn cố tình gọi API chấm công.
- **Giải pháp:** Xác thực `InternProfile` của user hiện tại có trạng thái hoạt động hợp lệ (`IN_PROGRESS`) hay không. Nếu không, trả về HTTP `400 Bad Request` hoặc `403 Forbidden`.

### 4.7. Case 7: Mã QR / Token phiên hết hạn (> 60 giây)
- **Vấn đề:** Intern quét định vị GPS hợp lệ, nhận mã QR nhưng không bấm xác nhận ngay mà chờ quá 60 giây mới gửi request xác nhận.
- **Giải pháp:** Backend kiểm tra thời điểm `expiresAt` của `qrToken`. Nếu `LocalDateTime.now().isAfter(expiresAt)`, lập tức từ chối và ném `BadRequestException` với thông báo: *"Mã QR xác thực đã hết hạn (chỉ có hiệu lực trong 60 giây). Vui lòng quét lại vị trí và thử lại."*.

### 4.8. Case 8: Replay Attack hoặc Giả Mạo Mã QR giữa các tài khoản
- **Vấn đề:** Intern dùng lại mã QR đã từng check-in trước đó, hoặc một intern khác cố tình đánh cắp token của bạn để check-in hộ.
- **Giải pháp:** 
  1. Mỗi token được cấp ngẫu nhiên qua UUID có tính bảo mật cao và chỉ sử dụng 1 lần duy nhất (`isConsumed = true`). Nếu token đã tiêu thụ, ném `BadRequestException` *"Mã QR xác thực này đã được sử dụng"*.
  2. Token được gắn chặt với `internId` của người khởi tạo. Nếu tài khoản gửi request xác nhận khác với chủ sở hữu token, ném `AccessDeniedException` *"Mã QR xác thực không thuộc về phiên của tài khoản này"*.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Kiểm tra trạng thái ngày hôm nay):** Hệ thống cung cấp API `GET /api/v1/attendances/today` trả về trạng thái chấm công của Intern trong ngày hiện tại (`NOT_CHECKED_IN`, `CHECKED_IN`, `COMPLETED`), kèm giờ check-in, giờ check-out, và thông tin văn phòng.
- **FR-2a (Khởi tạo Check-in & Sinh mã QR 60s):** Khi Intern bấm "Điểm Danh Vào Ca":
  - Client gửi tọa độ GPS lên `POST /api/v1/attendances/check-in/initiate`.
  - Backend tính khoảng cách Haversine. Nếu khoảng cách $\le 25\text{m}$, sinh `qrToken` ngẫu nhiên có hiệu lực 60 giây, mã hóa thành ảnh QR Base64 PNG.
  - Trả về mã QR, token, khoảng cách và thời gian hết hạn (`expiresInSeconds = 60`).
- **FR-2b (Xác nhận Check-in qua QR Token):** Khi Intern xác nhận:
  - Client gửi `qrToken` lên `POST /api/v1/attendances/check-in/confirm`.
  - Backend xác thực token hợp lệ, chưa hết hạn (< 60s), đúng tài khoản, chưa bị tiêu thụ.
  - Ghi nhận `check_in_time` bằng thời gian Server hiện tại, đánh dấu token đã sử dụng.
  - Nếu `check_in_time <= 08:15:00`: Gán trạng thái `status = ON_TIME`.
  - Nếu `check_in_time > 08:15:00`: Gán trạng thái `status = LATE`.
- **FR-3 (Check-out hợp lệ):** Cho phép Intern gửi tọa độ GPS để check-out sau khi đã check-in. Nếu khoảng cách $\le 25\text{m}$:
  - Ghi nhận `check_out_time` bằng thời gian Server hiện tại.
  - Tính tổng số giờ làm việc: `totalWorkingHours = Duration.between(checkInTime, checkOutTime).toMinutes() / 60.0` (làm tròn 2 chữ số thập phân).
  - Nếu `check_out_time < 17:30:00`:
    - Nếu trạng thái trước đó là `LATE` $\rightarrow$ Chuyển thành `LATE_AND_EARLY_LEAVE`.
    - Nếu trạng thái trước đó là `ON_TIME` $\rightarrow$ Chuyển thành `EARLY_LEAVE`.
- **FR-4 (Xem lịch sử cá nhân):** Hệ thống cung cấp API `GET /api/v1/attendances/my-history?month=M&year=Y` trả về danh sách toàn bộ ngày công trong tháng cùng tổng số ngày đi làm, tổng số lần đi muộn, tổng số giờ làm việc.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Công thức khoảng cách Haversine):**
  Khoảng cách $d$ (mét) giữa 2 điểm tọa độ $(\phi_1, \lambda_1)$ và $(\phi_2, \lambda_2)$ được tính bằng công thức:
  $$a = \sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta\lambda}{2}\right)$$
  $$c = 2 \cdot \text{atan2}\left(\sqrt{a}, \sqrt{1-a}\right)$$
  $$d = R \cdot c \quad (\text{với } R = 6,371,000\text{ m})$$
  Sai số cho phép: $d \le \text{allowedRadiusMeters}$ (mặc định là $25.0\text{m}$).
- **BR-2 (Khung giờ tiêu chuẩn & Ngưỡng đi muộn):**
  - Giờ bắt đầu làm việc: `08:00:00`.
  - Ngưỡng cho phép đúng giờ: Đến trước hoặc bằng `08:15:00`.
  - Ngưỡng đi muộn (`LATE`): Đến sau `08:15:00`.
  - Giờ kết thúc làm việc: `17:30:00`.
  - Về sớm (`EARLY_LEAVE`): Rời văn phòng trước `17:30:00`.
- **BR-3 (Tần suất chấm công):** Mỗi Intern chỉ có duy nhất 1 bản ghi `Attendance` cho mỗi ngày làm việc (`work_date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))`).
- **BR-4 (Quy đổi tổng thời gian làm việc):** `totalWorkingHours` được tính bằng số phút giữa Check-in và Check-out chia cho 60, làm tròn 2 chữ số thập phân.
- **BR-5 (Quản lý cấu hình tọa độ văn phòng):** Tọa độ văn phòng được lưu trong bảng `office_locations`. Nếu chưa có tọa độ tùy chỉnh của Admin, hệ thống tự động khởi tạo (seed) tọa độ mặc định ban đầu:
  - Tên văn phòng: `Trụ sở chính InternHub`
  - Tọa độ mặc định ban đầu: `latitude = 21.028511`, `longitude = 105.854444` (Hà Nội, Admin có thể cập nhật sau)
  - Bán kính cho phép: `25.0` mét.

---

## 7. Data Model (Mô Hình Dữ Liệu)

### 7.1. Cấu Trúc Bảng MySQL `attendances`
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | Định danh kế thừa từ `BaseEntity` |
| `intern_id` | `BIGINT` | `NOT NULL` | Foreign key logic tới `intern_profiles(id)` |
| `work_date` | `DATE` | `NOT NULL` | Ngày làm việc (`YYYY-MM-DD`) |
| `check_in_time` | `DATETIME` | `NOT NULL` | Thời điểm check-in (giờ server) |
| `check_out_time` | `DATETIME` | `NULL` | Thời điểm check-out (giờ server) |
| `check_in_latitude` | `DOUBLE` | `NULL` | Vĩ độ khi check-in (optional) |
| `check_in_longitude` | `DOUBLE` | `NULL` | Kinh độ khi check-in (optional) |
| `check_in_distance` | `DOUBLE` | `NULL` | Khoảng cách tính toán tới văn phòng (mét) |
| `check_out_latitude` | `DOUBLE` | `NULL` | Vĩ độ khi check-out |
| `check_out_longitude` | `DOUBLE` | `NULL` | Kinh độ khi check-out |
| `check_out_distance` | `DOUBLE` | `NULL` | Khoảng cách tính toán tới văn phòng khi check-out (mét) |
| `total_working_hours` | `DOUBLE` | `NULL` | Tổng số giờ làm việc (ví dụ: `8.5`) |
| `status` | `VARCHAR(30)` | `NOT NULL` | `ON_TIME`, `LATE`, `EARLY_LEAVE`, `LATE_AND_EARLY_LEAVE`, `FORGOTTEN_CHECKOUT` |
| `review_status` | `VARCHAR(32)` | `NOT NULL DEFAULT 'AUTO_APPROVED'` | `AUTO_APPROVED`, `PENDING_REVIEW`, `HR_APPROVED`, `HR_REJECTED` |
| `reviewed_by` | `VARCHAR(64)` | `NULL` | Định danh HR/Mentor đã xử lý duyệt |
| `reviewed_at` | `DATETIME` | `NULL` | Thời điểm xử lý duyệt |
| `review_note` | `TEXT` | `NULL` | Lý do phê duyệt hoặc từ chối ngày công |
| `notes` | `VARCHAR(255)` | `NULL` | Ghi chú thêm từ thực tập sinh |
| `created_at` | `DATETIME` | `NOT NULL` | Kế thừa từ `BaseEntity` |
| `updated_at` | `DATETIME` | `NULL` | Kế thừa từ `BaseEntity` |

**Ràng buộc Unique & Indexes:**
- Unique Constraint: `uk_intern_work_date` trên `(intern_id, work_date)`
- Index: `idx_attendance_intern_date` trên `(intern_id, work_date)`
- Index: `idx_attendance_status` trên `(status)`
- Index: `idx_attendance_review_status` trên `(review_status)`

---

### 7.2. Cấu Trúc Bảng MySQL `attendance_audit_logs` (Nhật ký bất biến)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | Định danh bản ghi audit |
| `attendance_id` | `BIGINT` | `NULL` | Liên kết tới bảng `attendances` (nếu đã tạo ca) |
| `intern_id` | `BIGINT` | `NOT NULL` | ID của thực tập sinh |
| `action_type` | `VARCHAR(20)` | `NOT NULL` | `CHECK_IN`, `CHECK_OUT`, `QUICK_CHECK_OUT` |
| `action_time` | `DATETIME` | `NOT NULL` | Thời điểm thực hiện trên server |
| `device_fingerprint`| `VARCHAR(128)`| `NOT NULL` | Mã định danh thiết bị di động |
| `device_model` | `VARCHAR(100)` | `NULL` | Tên thiết bị (User-Agent tóm tắt) |
| `client_ip` | `VARCHAR(45)` | `NOT NULL` | IP client gửi request |
| `is_company_ip` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` | Có thuộc dải IP văn phòng không |
| `latitude` | `DOUBLE` | `NULL` | Tọa độ GPS |
| `longitude` | `DOUBLE` | `NULL` | Tọa độ GPS |
| `accuracy_meters` | `DOUBLE` | `NULL` | Bán kính sai số GPS phần cứng |
| `distance_meters` | `DOUBLE` | `NULL` | Khoảng cách tới văn phòng |
| `trust_level` | `VARCHAR(20)` | `NOT NULL` | `HIGH`, `MEDIUM`, `LOW` |
| `audit_flags` | `VARCHAR(255)` | `NULL` | Chuỗi cờ: `NEW_DEVICE`, `CELLULAR_DATA_OK`, `NO_GPS_SUPPLIED`, v.v. |

---

### 7.3. Cấu Trúc Bảng MySQL `intern_devices` (Vòng đời thiết bị)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | ID thiết bị |
| `intern_id` | `BIGINT` | `NOT NULL` | ID của thực tập sinh |
| `device_fingerprint`| `VARCHAR(128)`| `NOT NULL` | Mã băm phần cứng trình duyệt/thiết bị |
| `device_name` | `VARCHAR(100)` | `NULL` | Tên hiển thị (ví dụ: "iPhone 15 Pro", "Samsung S23") |
| `trust_status` | `VARCHAR(30)` | `NOT NULL DEFAULT 'PENDING_PAIRING'` | `PRIMARY_TRUSTED`, `PENDING_PAIRING`, `BLOCKED` |
| `valid_checkin_count`| `INT` | `NOT NULL DEFAULT 0` | Số ngày check-in hợp lệ on-site để nâng hạng |
| `last_used_at` | `DATETIME` | `NOT NULL` | Lần cuối sử dụng |

---

### 7.4. JPA Entities & Enums Mapping
- **Enum `AttendanceStatus`:** `ON_TIME`, `LATE`, `EARLY_LEAVE`, `LATE_AND_EARLY_LEAVE`, `FORGOTTEN_CHECKOUT`, `ABSENT`.
- **Enum `AttendanceReviewStatus`:** `AUTO_APPROVED`, `PENDING_REVIEW`, `HR_APPROVED`, `HR_REJECTED`.
- **Enum `TrustLevel`:** `HIGH`, `MEDIUM`, `LOW`.

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

### 8.1. API Lấy Trạng Thái Chấm Công Hôm Nay: `GET /api/v1/attendances/today`
- **Mục đích:** Cung cấp dữ liệu tức thì cho Widget Check-in trên `intern/dashboard`.
- **Phân quyền:** `@PreAuthorize("hasRole('INTERN')")`
- **Request Headers:** `Authorization: Bearer <jwt>`

#### Response Success (200 OK - Trường hợp đã check-in nhưng chưa check-out):
```json
{
  "success": true,
  "message": "Lấy trạng thái chấm công hôm nay thành công",
  "data": {
    "workDate": "2026-09-29",
    "hasCheckedIn": true,
    "hasCheckedOut": false,
    "checkInTime": "2026-09-29T08:10:15",
    "checkOutTime": null,
    "totalWorkingHours": null,
    "status": "ON_TIME",
    "officeName": "Trụ sở chính InternHub",
    "allowedRadiusMeters": 25.0
  },
  "timestamp": "2026-09-29T08:10:20"
}
```

---

### 8.2. API Khởi Tạo Check-in & Sinh Mã QR: `POST /api/v1/attendances/check-in/initiate`
- **Mục đích:** Xác thực khoảng cách GPS và sinh mã QR phiên (hiệu lực 60 giây).
- **Phân quyền:** `@PreAuthorize("hasRole('INTERN')")`
- **Request Headers:**
  ```http
  Content-Type: application/json
  Authorization: Bearer <jwt>
  ```
- **Request Body JSON (`CheckInInitiateRequest`):**
  ```json
  {
    "latitude": 21.035665,
    "longitude": 105.768296,
    "clientBaseUrl": "http://192.168.1.15:5173" // Tùy chọn (nếu không truyền sẽ tự nhận diện qua header Origin/Referer hoặc default)
  }
  ```

#### Response Success (200 OK):
```json
{
  "success": true,
  "status": 200,
  "message": "Khoảng cách hợp lệ. Mã QR xác thực phiên điểm danh đã được tạo thành công.",
  "data": {
    "qrToken": "550e8400-e29b-41d4-a716-446655440000",
    "confirmationUrl": "http://192.168.1.15:5173/intern/attendance/confirm?token=550e8400-e29b-41d4-a716-446655440000",
    "qrCodeDataUrl": "data:image/png;base64,iVBORw0KGgo...",
    "expiresInSeconds": 60,
    "expiresAt": "2026-10-01T08:15:30",
    "distance": 5.4,
    "officeName": "Trụ sở chính InternHub"
  },
  "timestamp": "2026-10-01T08:14:30"
}
```

#### Response Error - Vượt Quá Bán Kính (400 Bad Request):
```json
{
  "success": false,
  "status": 400,
  "message": "Vị trí của bạn cách văn phòng 55.4m, vượt quá bán kính cho phép (25.0m)",
  "timestamp": "2026-10-01T08:14:30"
}
```

#### Response Error - Đã Check-in Trước Đó (409 Conflict):
```json
{
  "success": false,
  "status": 409,
  "message": "Bạn đã thực hiện check-in cho ngày hôm nay rồi",
  "timestamp": "2026-10-01T08:14:30"
}
```

---

### 8.3. API Xác Nhận Check-in Di Động: `POST /api/v1/attendances/check-in/confirm`
- **Mục đích:** Xác thực token phiên QR, thu thập dấu vân tay thiết bị, IP mạng, Optional GPS để chạy Trust Level Engine.
- **Phân quyền:** `@PreAuthorize("hasRole('INTERN')")`
- **Request Body JSON (`CheckInConfirmRequest`):**
  ```json
  {
    "qrToken": "550e8400-e29b-41d4-a716-446655440000",
    "deviceFingerprint": "a3f89b1c-7890-4123-bcde-9876543210ab",
    "deviceModel": "iPhone 15 Pro (Safari Mobile)",
    "latitude": 21.035665,
    "longitude": 105.768296,
    "accuracyMeters": 15.0,
    "notes": "Điểm danh vào ca"
  }
  ```

#### Response Success (200 OK - High/Medium Trust):
```json
{
  "success": true,
  "status": 200,
  "message": "Điểm danh vào ca thành công",
  "data": {
    "id": 101,
    "internId": 12,
    "workDate": "2026-10-12",
    "checkInTime": "2026-10-12T08:14:45",
    "status": "ON_TIME",
    "trustLevel": "HIGH",
    "reviewStatus": "AUTO_APPROVED",
    "auditFlags": "LOCATION_VERIFIED"
  },
  "timestamp": "2026-10-12T08:14:45"
}
```

#### Response Success (200 OK - Low Trust / Cần Duyệt):
```json
{
  "success": true,
  "status": 200,
  "message": "Điểm danh đã được ghi nhận và đang chờ HR xác nhận do bạn sử dụng thiết bị mới ngoài mạng văn phòng.",
  "data": {
    "id": 101,
    "internId": 12,
    "workDate": "2026-10-12",
    "checkInTime": "2026-10-12T08:14:45",
    "status": "ON_TIME",
    "trustLevel": "LOW",
    "reviewStatus": "PENDING_REVIEW",
    "auditFlags": "SUSPICIOUS_FRAUD_RISK"
  },
  "timestamp": "2026-10-12T08:14:45"
}
```

---

### 8.4. API Đồng Bộ Trạng Thái Real-time Cho Laptop: `GET /api/v1/attendances/check-in/stream`
- **Mục đích:** Kênh Server-Sent Events (SSE) để màn hình Modal Laptop lắng nghe tức thì sự kiện xác nhận từ điện thoại.
- **Phân quyền:** `@PreAuthorize("hasRole('INTERN')")`
- **Parameters:** `?qrToken=550e8400-e29b-41d4-a716-446655440000`
- **Response Type:** `text/event-stream`
- **Các sự kiện bắn về Laptop:**
  - Event `CONNECTED`: Laptop kết nối kênh lắng nghe thành công.
  - Event `CHECK_IN_SUCCESS`: Bắn ngay khi điện thoại xác nhận thành công $\rightarrow$ Laptop tự động chuyển giao diện xanh mừng thành công, tắt modal sau 3s và cập nhật Dashboard.
  - Event `TOKEN_EXPIRED`: Bắn khi token hết hạn $\rightarrow$ Laptop kích hoạt Prefetch đổi mã mới.

---

### 8.5. API Check-out Nhanh 1-Chạm Trên Điện Thoại: `POST /api/v1/attendances/check-out/quick`
- **Mục đích:** Cho phép TTS bấm Tan ca trực tiếp trên Web Mobile khi đã cất laptop vào balo (chỉ khả dụng khi đạt High/Medium trust).
- **Phân quyền:** `@PreAuthorize("hasRole('INTERN')")`
- **Request Body JSON (`QuickCheckOutRequest`):**
  ```json
  {
    "deviceFingerprint": "a3f89b1c-7890-4123-bcde-9876543210ab",
    "latitude": 21.035665,
    "longitude": 105.768296,
    "accuracyMeters": 20.0,
    "notes": "Tan ca đúng giờ"
  }
  ```
- **Xử lý:**
  - Backend kiểm tra IP mạng hoặc GPS $\le 50\text{m}$.
  - Nếu thỏa mãn: Ghi nhận `checkOutTime`, tính giờ làm việc, cập nhật trạng thái `status`.
  - Nếu không thỏa mãn (ở quán cà phê ngoài dải IP & GPS xa): Bị từ chối với HTTP 403 Forbidden kèm thông báo: *"Bạn đang ở ngoài khu vực văn phòng. Vui lòng quét mã QR trên laptop để thực hiện check-out."*

---

### 8.6. API Check-out Tiêu Chuẩn (QR Fallback): `POST /api/v1/attendances/check-out/confirm`
- **Mục đích:** Dự phòng quét mã QR trên laptop khi check-out bằng thiết bị lạ hoặc mạng chập chờn.

---

### 8.4. API Lịch Sử Chấm Công Cá Nhân: `GET /api/v1/attendances/my-history`
- **Query Params:** `month` (1-12), `year` (ví dụ: 2026). Nếu bỏ trống, mặc định lấy tháng và năm hiện tại.
- **Phân quyền:** `@PreAuthorize("hasRole('INTERN')")`

#### Response Success (200 OK):
```json
{
  "success": true,
  "message": "Lấy lịch sử chấm công thành công",
  "data": {
    "month": 9,
    "year": 2026,
    "totalWorkingDays": 22,
    "presentDays": 20,
    "lateDays": 2,
    "earlyLeaveDays": 1,
    "totalWorkingHours": 182.5,
    "attendances": [
      {
        "id": 101,
        "workDate": "2026-09-29",
        "checkInTime": "2026-09-29T08:10:15",
        "checkOutTime": "2026-09-29T17:35:00",
        "totalWorkingHours": 9.41,
        "status": "ON_TIME",
        "notes": "Hoàn thành công việc trong ngày"
      }
    ]
  },
  "timestamp": "2026-09-29T17:36:00"
}
```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi v2.0)

### 9.1. Sơ Đồ Luồng E2E Điểm Danh Đa Tầng (Dynamic QR + SSE + Mobile Confirm):
```text
[TTS trên Laptop Dashboard]                      [Điện Thoại Di Động TTS]              [Backend Microservice & Redis]
            │                                               │                                         │
            ├─ 1. Bấm "Vào ca" ─────────────────────────────┼────────────────────────────────────────►│
            │                                               │                                         ├─ Sinh qrToken (60s) vào Redis
            │◄─ 2. Trả về qrCodeDataUrl + qrToken ──────────┼─────────────────────────────────────────┤
            │                                               │                                         │
            ├─ 3. Mở kết nối SSE GET /check-in/stream ──────┼────────────────────────────────────────►│ (Lắng nghe sự kiện SSE)
            │                                               │                                         │
            │  [Màn hình Laptop hiện QR 60s]                │                                         │
            │  (Vòng đếm ngược Radial Progress)            │                                         │
            │                                               │                                         │
            │  ── 4. Dùng Camera quét mã QR trên laptop ──► │                                         │
            │                                               ├─ 5. Mở Confirm Page (/confirm?token=)   │
            │                                               ├─ 6. Thu thập Device Fingerprint + GPS   │
            │                                               ├─ 7. Gửi POST /check-in/confirm ────────►│
            │                                               │                                         ├─ Đánh giá Trust Level (Device + IP + GPS)
            │                                               │                                         ├─ Lưu Attendance + Audit Log
            │                                               │                                         ├─ BẮN SỰ KIỆN SSE (CHECK_IN_SUCCESS) ──┐
            │                                               │◄─ 8. Trả về HTTP 200 (Xanh/Vàng) ───────┤                                       │
            │                                               │                                         │                                       │
            │◄──────────────────────────────────────────────┴─────────────────────────────────────────┴───────────────────────────────────────┘
            │ (Nhận SSE CHECK_IN_SUCCESS)
            ├─ Màn hình Laptop tự động nổ chúc mừng xanh lá
            ├─ Tự động đóng Modal sau 3s
            └─ Tự động cập nhật Card trạng thái trên Dashboard ("Đã vào ca: 08:14")
```

---

### 9.2. Ma Trận 12 Điểm Nâng Cấp Trải Nghiệm Doanh Nghiệp (Enterprise UX Requirements)

| Nhóm | Điểm | Tên tính năng trải nghiệm | Hành vi chi tiết |
| :--- | :---: | :--- | :--- |
| **Laptop Modal** | **1** | **Vòng Đếm Ngược & Silent Prefetch** | Vòng tròn SVG đếm ngược 60s $\rightarrow$ 0s. Khi còn 5s cuối, tự gọi API lấy mã mới cross-fade mượt mà, không gián đoạn. Tự pause sau 3 phút nếu không quét. |
| | **2** | **Đồng Bộ Tức Thì Qua SSE (Real-time Sync)** | Laptop mở kết nối Server-Sent Events. Điện thoại bấm xong là Laptop nổ hiệu ứng thành công ngay lập tức, tự đóng modal và reload card. |
| | **3** | **Onboarding Micro-Guide** | Minh họa động hướng dẫn TTS quét bằng Camera thường hoặc Zalo / Google Lens. |
| **Mobile Confirm** | **4** | **Pre-permission Thấu Cảm** | Banner màu xanh giải thích lý do trước khi xin quyền GPS. Nếu từ chối GPS vẫn cho tiếp tục và chuyển sang kiểm tra mạng Wi-Fi công ty (`MEDIUM_TRUST`). |
| | **5** | **Smart Retry & Grace Period** | Tự động thử lại 3 lần nếu sóng 4G chập chờn. Cho phép sai số lệch đồng hồ 15s (`clock skew grace`) cho Redis token. |
| | **6** | **Thẻ Trạng Thái Minh Bạch** | Xanh khi `APPROVED`. Vàng khi `PENDING_REVIEW` với lời nhắn trấn an: *"Đang chờ HR đối soát nhẹ nhàng do bạn dùng thiết bị mới ngoài công ty. Ngày công không bị mất."* |
| **Tan Ca (Check-out)** | **7** | **Tan Ca Nhanh 1-Chạm (Mobile Quick Check-out)** | Bấm trực tiếp trên Web di động khi đang kết nối Wi-Fi công ty hoặc GPS $\le 50\text{m}$. Không bắt mở lại laptop khi đã cất vào balo. |
| | **8** | **Nhắc Nhở Tan Ca (End-of-Shift Toast)** | Thông báo nhắc nhở nhẹ nhàng lúc 17:30 để tránh quên check-out. |
| **Vận Hành & Ổn Định**| **9** | **Tự Động Đóng Ca EOD 23:59 & Giải Trình** | Scheduled Job 23:59 đóng các ca quên check-out (`FORGOTTEN_CHECKOUT`). Sáng hôm sau hiện popup nộp đơn giải trình giờ về cho Mentor duyệt 1-click. |
| | **10**| **Idempotency Guard (Chống Click Trùng)** | Redis Distributed Lock (`internId + workDate`) + Disable nút trên UI ngăn bấm lặp request. |
| | **11**| **Vòng Đời Thiết Bị (Primary Device Pairing)**| Lưu thiết bị mới là `PENDING_PAIRING`. Sau 3 ngày điểm danh hợp lệ on-site, tự động thăng hạng thành `PRIMARY_TRUSTED`. |
| | **12**| **Bù Sai Số Tòa Nhà (Indoor Signal Fallback)** | Tính toán khoảng cách bù trừ bán kính sai số phần cứng: `distance - accuracy <= allowedRadius`. Không chặn oan trong phòng họp kín. |

---

## 10. Non-Functional Requirements & Constraints

- **Hiệu năng & Độ trễ:** Tính toán công thức Haversine thuần túy trên RAM, thời gian xử lý API check-in/out $< 150\text{ms}$.
- **Chính xác múi giờ:** Cố định `ZoneId.of("Asia/Ho_Chi_Minh")` cho toàn bộ các phép so sánh ngày giờ, tránh phụ thuộc vào timezone của máy chủ hosting.
- **Tiêu chuẩn Anti-God-Class:** Tách biệt `HaversineDistanceCalculator` thành Component tiện ích riêng biệt, giữ cho `AttendanceServiceImpl` ngắn gọn, tập trung dưới 250 dòng.
- **Quản lý giao dịch:** Áp dụng `@Transactional(readOnly = true)` tại Class cấp Service và `@Transactional` tường minh trên method `checkIn()` và `checkOut()`.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [x] **AC-1:** Intern trong bán kính $\le 25\text{m}$ gửi request check-in trước $08:15$ nhận về HTTP 201 Created với trạng thái `ON_TIME`.
- [x] **AC-2:** Intern trong bán kính $\le 25\text{m}$ gửi request check-in sau $08:15$ nhận về HTTP 201 Created với trạng thái `LATE`.
- [x] **AC-3:** Intern gửi tọa độ cách văn phòng $> 25\text{m}$ bị từ chối với HTTP 400 Bad Request và thông báo khoảng cách chi tiết.
- [x] **AC-4:** Intern check-in lần thứ 2 trong cùng một ngày bị từ chối với HTTP 409 Conflict.
- [x] **AC-5:** Intern check-out khi chưa từng check-in trong ngày bị từ chối với HTTP 400 Bad Request.
- [x] **AC-6:** Intern check-out hợp lệ trước $17:30$ được cập nhật trạng thái `EARLY_LEAVE` hoặc `LATE_AND_EARLY_LEAVE` kèm tổng giờ làm việc chính xác.
- [x] **AC-7:** Intern check-out lần thứ 2 trong cùng ngày bị từ chối với HTTP 400 Bad Request.
- [x] **AC-8:** Gọi `GET /api/v1/attendances/today` phản ánh chính xác trạng thái thực tế của ngày hiện tại.

---

## 12. Unit & Integration Test Cases Checklist

- [x] **UT-BE-01:** `testHaversineDistanceCalculator_within25Meters_shouldReturnTrue()`
- [x] **UT-BE-02:** `testHaversineDistanceCalculator_beyond25Meters_shouldReturnFalse()`
- [x] **UT-BE-03:** `checkIn_whenValidAndBefore0815_shouldReturnOnTime()`
- [x] **UT-BE-04:** `checkIn_whenValidAndAfter0815_shouldReturnLate()`
- [x] **UT-BE-05:** `checkIn_whenAlreadyCheckedIn_shouldThrowDuplicateResourceException()`
- [x] **UT-BE-06:** `checkIn_whenBeyondAllowedRadius_shouldThrowBadRequestException()`
- [x] **UT-BE-07:** `checkOut_whenNotCheckedIn_shouldThrowBadRequestException()`
- [x] **UT-BE-08:** `checkOut_whenValid_shouldCalculateHoursAndUpdateStatus()`
- [x] **IT-BE-01:** Kiểm thử tích hợp các endpoint qua Controller Unit Test (`AttendanceControllerTest`).

---

## 13. Implementation Checklist (Danh Sách File Triển Khai)

- [x] **Entity:**
  - `org.example.internservice.attendance.entity.Attendance`
  - `org.example.internservice.attendance.entity.OfficeLocation`
  - `org.example.internservice.attendance.entity.enums.AttendanceStatus`
- [x] **DTOs:**
  - `org.example.internservice.attendance.dto.request.CheckInRequest`
  - `org.example.internservice.attendance.dto.request.CheckOutRequest`
  - `org.example.internservice.attendance.dto.response.AttendanceResponse`
  - `org.example.internservice.attendance.dto.response.TodayAttendanceResponse`
  - `org.example.internservice.attendance.dto.response.MonthlyAttendanceSummaryResponse`
- [x] **Repository:**
  - `org.example.internservice.attendance.repository.AttendanceRepository`
  - `org.example.internservice.attendance.repository.OfficeLocationRepository`
- [x] **Utils / Helper:**
  - `org.example.internservice.attendance.util.HaversineDistanceCalculator`
- [x] **Service:**
  - `org.example.internservice.attendance.service.AttendanceService`
  - `org.example.internservice.attendance.service.impl.AttendanceServiceImpl`
- [x] **Controller:**
  - `org.example.internservice.attendance.controller.AttendanceController`
- [x] **Tests:**
  - `org.example.internservice.attendance.util.HaversineDistanceCalculatorTest`
  - `org.example.internservice.attendance.service.impl.AttendanceServiceImplTest`
  - `org.example.internservice.attendance.controller.AttendanceControllerTest`
