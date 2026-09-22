# Specification: Sao Lưu Dữ Liệu Định Kỳ Hệ Thống (System Data Backup)

---

## 1. Feature Overview (Tổng Quan Tính Năng)
- **Feature Name:** Sao lưu dữ liệu định kỳ để đảm bảo an toàn hệ thống (Periodic System Data Backup & Management)
- **Jira Ticket:** [TM-8](https://robluccibn9935.atlassian.net/browse/TM-8)
- **Target Subsystems:** `employee-service` (Core Service/Scheduled Task), `mysql-db` (Database Server), `docker-compose.yml` (Hạ tầng lưu trữ Volume)
- **Target Users:** 
  - ⚙️ **Hệ thống (System):** Tự động kích hoạt sao lưu theo lịch định kỳ (Cron Job), xoay vòng dữ liệu cũ.
  - 🛡️ **Quản trị viên (Admin):** Kích hoạt sao lưu tức thời, theo dõi danh sách lịch sử sao lưu, tải file bản sao lưu (`.sql.gz`).
- **Phân loại thay đổi (Change Level):** **L3** (Ảnh hưởng Database bảng mới, Cron Background Job, File I/O & Storage, Admin API)

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)
Đảm bảo an toàn, tính liên tục và khả năng phục hồi dữ liệu trước các sự cố phần cứng, lỗi phần mềm hoặc thao tác nhầm lẫn:
1. **Hiện thực hóa vai trò Hệ thống (System):** Đáp ứng đúng trách nhiệm tác vụ ngầm tự động đã được quy định trong tài liệu kiến trúc dự án ([README.md](file:///c:/Users/Admin/InternHub/README.md#L108)).
2. **Tự động hóa hoàn toàn:** Hệ thống tự động thực hiện snapshot toàn bộ cơ sở dữ liệu `internhub_db` vào khung giờ thấp điểm (02:00 sáng hàng ngày) mà không cần sự can thiệp thủ công của con người.
3. **Chủ động ứng phó sự cố (On-demand Backup):** Cung cấp công cụ cho Admin chủ động kích hoạt sao lưu tức thì trước khi nâng cấp hệ thống hoặc triển khai phiên bản mới.
4. **Minh bạch & Kiểm soát lịch sử:** Lưu vết đầy đủ trạng thái (`SUCCESS`, `FAILED`, `IN_PROGRESS`), kích thước bản sao lưu, thời gian thực hiện và nguyên nhân lỗi nếu có.
5. **Tiết kiệm tài nguyên & Chống tràn đĩa (Retention Policy):** Tự động dọn dẹp các bản sao lưu cũ quá thời hạn quy định (mặc định 30 ngày) để bảo vệ dung lượng lưu trữ của máy chủ.

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)
- **Tạo bảng `backup_history`:** Lưu trữ lịch sử, trạng thái và siêu dữ liệu (metadata) của các phiên sao lưu.
- **Tác vụ sao lưu tự động định kỳ (`@Scheduled`):** Thiết lập Cron Job chạy ngầm hàng ngày vào lúc 02:00 AM (`0 0 2 * * ?`).
- **Cơ chế trích xuất dữ liệu an toàn:** Thực thi `mysqldump` với cờ `--single-transaction --quick` để snapshot dữ liệu nhất quán mà không gây khóa bảng (non-blocking). Nén file dưới định dạng chuẩn `.sql.gz` để tối ưu dung lượng.
- **Cấu hình Volume lưu trữ bền vững:** Định nghĩa mount volume `./data/backups:/backups` trong Docker Compose.
- **Bộ API Quản trị dành cho Admin & System:**
  - `GET /api/system/backups`: Lấy danh sách lịch sử sao lưu (hỗ trợ phân trang, lọc theo trạng thái và khoảng thời gian).
  - `POST /api/system/backups`: Kích hoạt sao lưu thủ công tức thì (On-demand).
  - `GET /api/system/backups/{id}/download`: Tải file bản sao lưu vật lý về máy trạm.
  - `DELETE /api/system/backups/{id}`: Xóa một bản sao lưu và loại bỏ file vật lý tương ứng.
- **Cơ chế dọn dẹp xoay vòng (Retention Policy):** Tự động quét và xóa các bản sao lưu cùng file vật lý đã quá 30 ngày.

### 3.2. Ngoài phạm vi (Out of Scope) - *Ngăn chặn over-engineering*
- **Không tích hợp dịch vụ Cloud Storage bên thứ ba (AWS S3, GCS, Azure Blob):** Tập trung lưu trữ an toàn trên máy chủ nội bộ qua Docker Volume trước; việc đẩy lên Cloud sẽ tách thành ticket riêng `TM-8-S3`.
- **Không tự động chạy lệnh Restore trực tiếp qua API công khai 1-click:** Việc phục hồi dữ liệu từ file backup mang rủi ro ghi đè dữ liệu cực kỳ lớn; hệ thống sẽ cung cấp tài liệu hướng dẫn khôi phục qua CLI an toàn thay vì mở API công khai.
- **Không sao lưu phân tán cho NoSQL/Redis:** Hệ thống hiện tại chỉ sử dụng MySQL 8.0 làm nguồn dữ liệu chính.

---

## 4. Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Edge Cases)

### 4.1. Case 1: Xung đột khi sao lưu song song (Concurrent Backups)
- **Vấn đề:** Khi Scheduled Job đang chạy lúc 02:00 sáng, Admin đồng thời bấm nút "Sao lưu ngay" trên hệ thống, dẫn đến 2 tiến trình `mysqldump` chạy song song, gây nghẽn I/O và tạo 2 file trùng lặp.
- **Khắc phục:** 
  - Sử dụng cơ chế khóa phân tán hoặc cờ nguyên tử `AtomicBoolean isBackupRunning`.
  - Kiểm tra bảng `backup_history`: nếu có bất kỳ bản ghi nào đang có `status = 'IN_PROGRESS'`, từ chối ngay request mới và trả về HTTP `409 Conflict` kèm thông báo: *"Hệ thống đang trong quá trình sao lưu, vui lòng thử lại sau."*

### 4.2. Case 2: Đầy ổ cứng máy chủ (Disk Space Exhaustion)
- **Vấn đề:** Quá trình sao lưu đang diễn ra thì máy chủ hết dung lượng đĩa cứng, dẫn đến file `.sql.gz` bị hỏng, dịch vụ MySQL có thể bị crash.
- **Khắc phục:**
  - Trước khi khởi chạy `mysqldump`, kiểm tra dung lượng trống khả dụng của thư mục lưu trữ (`File.getUsableSpace()`). Yêu cầu tối thiểu 1GB dung lượng trống.
  - Nếu xảy ra lỗi hết dung lượng trong quá trình dump: xóa ngay lập tức file rác tạm thời (`.tmp`), cập nhật trạng thái bản ghi thành `FAILED` với thông báo lỗi cụ thể.

### 4.3. Case 3: Tiến trình bị ngắt đột ngột do Container restart (Zombie Process)
- **Vấn đề:** Khi đang dump dữ liệu, container bị restart hoặc mất điện, bản ghi trong DB vĩnh viễn bị kẹt ở trạng thái `IN_PROGRESS`.
- **Khắc phục:** 
  - Khi service khởi động (`@PostConstruct` hoặc `ApplicationReadyEvent`), quét toàn bộ các bản ghi `IN_PROGRESS` có thời gian tạo quá 30 phút và tự động cập nhật về `FAILED` kèm thông điệp: *"Tiến trình bị gián đoạn do khởi động lại dịch vụ"*.

### 4.4. Case 4: Khóa bảng gây nghẽn ứng dụng trong giờ làm việc (Table Lock Contention)
- **Vấn đề:** Nếu Admin kích hoạt sao lưu thủ công vào ban ngày mà câu lệnh dump gây khóa bảng, toàn bộ thao tác nộp hồ sơ của thực tập sinh và chấm công sẽ bị treo.
- **Khắc phục:** 
  - Bắt buộc cấu hình `mysqldump` với các tham số: `--single-transaction --quick --skip-lock-tables` trên storage engine InnoDB. 
  - Đảm bảo snapshot dữ liệu đạt tính nhất quán ACID mà không khóa bất kỳ bảng dữ liệu nào.

### 4.5. Case 5: Nguy cơ tấn công dò quét đường dẫn (Path Traversal Vulnerability khi tải file)
- **Vấn đề:** Người dùng cố tình truyền tham số tải file chứa ký tự đặc biệt (ví dụ `../../etc/passwd` hoặc file cấu hình hệ thống).
- **Khắc phục:**
  - API download chỉ nhận tham số duy nhất là `id` dạng số nguyên (`BIGINT`), không nhận tên file hoặc đường dẫn từ phía client.
  - Tầng Service truy vấn bản ghi từ DB để lấy tên file đã được mã hóa an toàn (`internhub_backup_YYYYMMDD_HHmmss.sql.gz`) và kiểm tra đường dẫn tuyệt đối phải nằm trong thư mục gốc được chỉ định (`/backups`).

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Tự động sao lưu định kỳ):** Hệ thống tự động kích hoạt tiến trình sao lưu cơ sở dữ liệu `internhub_db` vào lúc 02:00:00 sáng mỗi ngày.
- **FR-2 (Kích hoạt sao lưu thủ công):** Quản trị viên (Admin) có thể chủ động kích hoạt sao lưu bất kỳ lúc nào qua REST API.
- **FR-3 (Nén và định dạng file):** Bản sao lưu được xuất dưới dạng file SQL và tự động nén gzip (`.sql.gz`), đặt tên theo định dạng chuẩn: `internhub_backup_YYYYMMDD_HHmmss.sql.gz`.
- **FR-4 (Ghi nhận nhật ký sao lưu):** Ghi nhận chi tiết vào bảng `backup_history`: tên file, dung lượng (bytes), đường dẫn lưu trữ, loại sao lưu (`AUTOMATIC` hoặc `MANUAL`), trạng thái (`IN_PROGRESS`, `SUCCESS`, `FAILED`), thời gian thực hiện, thời gian hoàn thành.
- **FR-5 (Truy vấn danh sách sao lưu):** Cung cấp API trả về danh sách lịch sử các bản sao lưu hỗ trợ phân trang (Pagination), sắp xếp theo ngày tạo mới nhất, lọc theo trạng thái.
- **FR-6 (Tải bản sao lưu an toàn):** Cho phép Admin tải file `.sql.gz` về máy tính thông qua HTTP Streaming an toàn.
- **FR-7 (Tự động xoay vòng dữ liệu):** Mỗi khi một tác vụ sao lưu thành công, hệ thống tự động xóa các bản sao lưu cũ hơn 30 ngày (xóa cả bản ghi trong DB lẫn file vật lý trên đĩa).
- **FR-8 (Xóa bản sao lưu chủ động):** Cho phép Admin xóa một bản sao lưu cụ thể khi không còn nhu cầu lưu trữ.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Quyền hạn truy cập):** Tất cả các endpoint thuộc nhóm `/api/system/backups/**` chỉ được truy cập bởi tài khoản có quyền `ADMIN` hoặc token nội bộ của `SYSTEM`.
- **BR-2 (Giới hạn thực thi đồng thời):** Tại một thời điểm, toàn hệ thống chỉ cho phép duy nhất 01 tiến trình sao lưu được chạy.
- **BR-3 (Thời hạn lưu trữ - Retention Period):** Bản sao lưu có thời hạn lưu trữ mặc định là **30 ngày**. Bản sao lưu vượt quá 30 ngày sẽ tự động bị xóa trong đợt dọn dẹp hàng ngày.
- **BR-4 (Định dạng nén bắt buộc):** Toàn bộ file sao lưu phải được nén định dạng gzip (`.gz`) để giảm thiểu ít nhất 70% dung lượng so với file SQL thô.
- **BR-5 (Toàn vẹn dữ liệu):** Thao tác sao lưu không được phép làm gián đoạn hoặc khóa việc đọc/ghi dữ liệu của các chức năng nghiệp vụ khác (`--single-transaction`).
- **BR-6 (Dung lượng đĩa an toàn):** Dung lượng đĩa khả dụng phải $\ge 1.0\text{ GB}$ trước khi tiến hành tạo bản sao lưu mới.

---

## 7. Data Model (Mô Hình Dữ Liệu)

Kế thừa `BaseEntity` (`id` BIGINT AUTO_INCREMENT, `created_at` DATETIME, `updated_at` DATETIME).

### 7.1. Bảng `backup_history`
```sql
CREATE TABLE backup_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,          -- Kích thước file tính bằng bytes
    backup_type VARCHAR(20) NOT NULL,             -- 'AUTOMATIC', 'MANUAL'
    status VARCHAR(20) NOT NULL,                  -- 'IN_PROGRESS', 'SUCCESS', 'FAILED'
    scope VARCHAR(50) NOT NULL DEFAULT 'ALL',     -- Phạm vi: 'ALL' (toàn bộ database)
    duration_ms BIGINT NULL,                      -- Thời gian thực thi tính bằng mili-giây
    error_message TEXT NULL,                      -- Chi tiết lỗi nếu thất bại
    created_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_backup_status (status),
    INDEX idx_backup_type (backup_type),
    INDEX idx_backup_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 7.2. Các Enum nghiệp vụ
- **`BackupType`:**
  - `AUTOMATIC`: Do hệ thống chạy định kỳ theo cron.
  - `MANUAL`: Do Admin kích hoạt thủ công qua API.
- **`BackupStatus`:**
  - `IN_PROGRESS`: Đang trong tiến trình trích xuất và nén.
  - `SUCCESS`: Sao lưu thành công và file hợp lệ.
  - `FAILED`: Thất bại (ghi rõ nguyên nhân trong `errorMessage`).

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

- **Base URL:** `/api/system/backups`
- **Content-Type:** `application/json` (trừ endpoint download trả về `application/gzip`)

### 8.1. Kích hoạt sao lưu thủ công (Trigger Backup)
- **Method:** `POST`
- **Path:** `/api/system/backups`
- **Headers:** `Authorization: Bearer <ADMIN_JWT_TOKEN>`
- **Request Body:** Không có (hoặc rỗng)

#### Response: `201 Created`
```json
{
  "code": 201,
  "message": "Tiến trình sao lưu dữ liệu đã được khởi chạy thành công",
  "data": {
    "id": 12,
    "fileName": "internhub_backup_20260921_094500.sql.gz",
    "fileSize": 1548201,
    "backupType": "MANUAL",
    "status": "SUCCESS",
    "scope": "ALL",
    "durationMs": 3250,
    "createdBy": "admin@internhub.com",
    "createdAt": "2026-09-21T09:45:00"
  }
}
```

#### Response Lỗi:
- **`409 Conflict`** (Đang có tiến trình sao lưu khác chạy):
  ```json
  {
    "code": 409,
    "message": "Một tiến trình sao lưu khác đang diễn ra. Vui lòng thử lại sau."
  }
  ```
- **`500 Internal Server Error`** (Lỗi dung lượng hoặc lỗi hệ thống):
  ```json
  {
    "code": 500,
    "message": "Sao lưu thất bại: Dung lượng đĩa trống không đủ (yêu cầu tối thiểu 1GB)."
  }
  ```

---

### 8.2. Lấy danh sách lịch sử sao lưu (Get Backup History)
- **Method:** `GET`
- **Path:** `/api/system/backups?page=0&size=10&status=SUCCESS`
- **Query Parameters:**
  - `page` (int, default: 0): Số trang.
  - `size` (int, default: 10): Số bản ghi mỗi trang.
  - `status` (string, optional): Lọc theo `SUCCESS`, `FAILED`, `IN_PROGRESS`.
  - `backupType` (string, optional): Lọc theo `AUTOMATIC`, `MANUAL`.

#### Response: `200 OK`
```json
{
  "code": 200,
  "message": "Lấy danh sách lịch sử sao lưu thành công",
  "data": {
    "content": [
      {
        "id": 12,
        "fileName": "internhub_backup_20260921_094500.sql.gz",
        "fileSize": 1548201,
        "formattedSize": "1.48 MB",
        "backupType": "MANUAL",
        "status": "SUCCESS",
        "durationMs": 3250,
        "createdBy": "admin@internhub.com",
        "createdAt": "2026-09-21T09:45:00"
      },
      {
        "id": 11,
        "fileName": "internhub_backup_20260921_020000.sql.gz",
        "fileSize": 1542110,
        "formattedSize": "1.47 MB",
        "backupType": "AUTOMATIC",
        "status": "SUCCESS",
        "durationMs": 2840,
        "createdBy": "SYSTEM",
        "createdAt": "2026-09-21T02:00:00"
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "pageNumber": 0,
    "pageSize": 10
  }
}
```

---

### 8.3. Tải file bản sao lưu (Download Backup File)
- **Method:** `GET`
- **Path:** `/api/system/backups/{id}/download`
- **Headers:** `Authorization: Bearer <ADMIN_JWT_TOKEN>`

#### Response: `200 OK`
- **Headers:**
  - `Content-Type: application/gzip`
  - `Content-Disposition: attachment; filename="internhub_backup_20260921_094500.sql.gz"`
  - `Content-Length: 1548201`
- **Body:** Dữ liệu binary của file `.sql.gz`.

#### Response Lỗi:
- **`404 Not Found`** (Không tìm thấy bản ghi hoặc file vật lý không tồn tại trên đĩa):
  ```json
  {
    "code": 404,
    "message": "Bản sao lưu với ID 12 không tồn tại hoặc file vật lý đã bị xóa."
  }
  ```

---

### 8.4. Xóa một bản sao lưu (Delete Backup)
- **Method:** `DELETE`
- **Path:** `/api/system/backups/{id}`
- **Headers:** `Authorization: Bearer <ADMIN_JWT_TOKEN>`

#### Response: `200 OK`
```json
{
  "code": 200,
  "message": "Xóa bản sao lưu thành công"
}
```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)

### 9.1. Luồng Sao Lưu Tự Động Định Kỳ (System Scheduled Flow)
```text
Cron Trigger (02:00 AM)
   │
   ▼
1. Kiểm tra cờ IsBackupRunning?
   ├── Nếu True  --> Ghi Log cảnh báo, bỏ qua lượt này
   └── Nếu False --> Đặt IsBackupRunning = True
   │
   ▼
2. Kiểm tra dung lượng đĩa khả dụng (>= 1GB)?
   ├── Nếu False --> Ghi bản ghi FAILED vào DB, bắn cảnh báo
   └── Nếu True  --> Tiếp tục
   │
   ▼
3. Tạo bản ghi backup_history với status = 'IN_PROGRESS'
   │
   ▼
4. Khởi chạy tiến trình trích xuất:
   mysqldump --single-transaction --quick ... | gzip > /backups/internhub_backup_YYYYMMDD_HHmmss.sql.gz
   │
   ▼
5. Kết quả tiến trình:
   ├── THÀNH CÔNG: Cập nhật status = 'SUCCESS', fileSize, durationMs
   └── THẤT BẠI:   Xóa file tạm, cập nhật status = 'FAILED', ghi errorMessage
   │
   ▼
6. Kích hoạt Retention Cleanup:
   Quét & xóa các bản sao lưu cũ > 30 ngày (cả file vật lý và DB)
   │
   ▼
7. Giải phóng cờ IsBackupRunning = False
```

---

## 10. Non-Functional Requirements & Constraints (Yêu Cầu Phi Chức Năng)

1. **Hiệu năng & Tác động hệ thống:**
   - Sử dụng `--single-transaction` và `--quick` để bộ nhớ RAM tiêu thụ của `mysqldump` là tối thiểu, stream thẳng dữ liệu qua gzip ra file đĩa.
   - Thời gian thực thi không được vượt quá 5 phút cho cơ sở dữ liệu dưới 10GB.
2. **Cấu hình Docker Compose:**
   - Container chạy tác vụ phải được mount volume dùng chung:
     ```yaml
     volumes:
       - ./data/backups:/backups
     ```
3. **Bảo mật & Phân quyền:**
   - Toàn bộ các API sao lưu phải bắt buộc xác thực quyền `ADMIN` hoặc token nội bộ của `SYSTEM`.
   - File backup chứa toàn bộ dữ liệu quan trọng của doanh nghiệp nên thư mục lưu trữ phải được phân quyền `chmod 700` hoặc giới hạn truy cập nội bộ trong mạng Docker.
4. **Audit Logging:**
   - Mọi hành động kích hoạt sao lưu thủ công, xóa bản sao lưu, hoặc tải bản sao lưu đều phải được ghi log kèm username của Admin thực hiện.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [ ] **AC-1 (Định kỳ tự động):** Khi đến 02:00:00 sáng, hệ thống tự động khởi chạy tiến trình sao lưu, sinh ra file `.sql.gz` hợp lệ trong thư mục cấu hình và ghi bản ghi `SUCCESS` vào bảng `backup_history`.
- [ ] **AC-2 (Kích hoạt thủ công):** Admin gọi `POST /api/system/backups` $\rightarrow$ Nhận HTTP `201 Created` và file sao lưu được tạo thành công ngay lập tức.
- [ ] **AC-3 (Chặn chạy đồng thời):** Khi một tiến trình sao lưu đang diễn ra (`IN_PROGRESS`), bất kỳ request sao lưu mới nào gửi đến đều bị chặn và nhận HTTP `409 Conflict`.
- [ ] **AC-4 (Kiểm tra dung lượng):** Nếu ổ cứng còn trống dưới 1GB, hệ thống từ chối sao lưu và trả về mã lỗi `500` kèm thông báo không đủ dung lượng.
- [ ] **AC-5 (Danh sách & Phân trang):** Gọi `GET /api/system/backups` trả về đúng định dạng danh sách phân trang kèm dung lượng đã định dạng (ví dụ: `1.48 MB`).
- [ ] **AC-6 (Tải file an toàn):** Gọi `GET /api/system/backups/{id}/download` tải về đúng file `.sql.gz` có thể giải nén và khôi phục được dữ liệu.
- [ ] **AC-7 (Chống Path Traversal):** Truyền id không hợp lệ hoặc tìm cách truy cập file ngoài thư mục `/backups` đều bị chặn và trả về `404 Not Found`.
- [ ] **AC-8 (Tự động xoay vòng 30 ngày):** Các bản sao lưu có `created_at` cách thời điểm hiện tại hơn 30 ngày tự động bị xóa sạch cả trong database lẫn file vật lý trên đĩa cứng.

---

## 12. Unit & Integration Test Checklist

### 12.1. Unit Tests (`SystemBackupServiceTest.java`)
- [ ] `UT-BE-01`: `triggerBackup_whenNoProcessRunning_shouldExecuteSuccessfully()`
- [ ] `UT-BE-02`: `triggerBackup_whenAnotherProcessRunning_shouldThrowConflictException()`
- [ ] `UT-BE-03`: `triggerBackup_whenDiskSpaceInsufficient_shouldThrowStorageException()`
- [ ] `UT-BE-04`: `cleanOldBackups_shouldDeleteFilesOlderThanRetentionDays()`
- [ ] `UT-BE-05`: `downloadBackup_whenFileExists_shouldReturnValidResource()`
- [ ] `UT-BE-06`: `downloadBackup_whenRecordNotFound_shouldThrowResourceNotFoundException()`

### 12.2. Integration Tests (`SystemBackupControllerTest.java`)
- [ ] `IT-BE-01`: `POST /api/system/backups` với quyền ADMIN $\rightarrow$ Trả về `201 Created`.
- [ ] `IT-BE-02`: `POST /api/system/backups` không có token $\rightarrow$ Trả về `401 Unauthorized` hoặc `403 Forbidden`.
- [ ] `IT-BE-03`: `GET /api/system/backups` $\rightarrow$ Trả về `200 OK` với cấu trúc `ApiResponse<Page<BackupResponse>>`.
- [ ] `IT-BE-04`: `GET /api/system/backups/{id}/download` $\rightarrow$ Trả về `200 OK` với header `Content-Type: application/gzip`.
- [ ] `IT-BE-05`: `DELETE /api/system/backups/{id}` $\rightarrow$ Trả về `200 OK` và xóa file thành công.

---

## 13. Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai)

- [ ] **Docker Compose:** Cập nhật [`docker-compose.yml`](file:///c:/Users/Admin/InternHub/docker-compose.yml) bổ sung volume mount `./data/backups:/backups`.
- [ ] **Configuration:** Thêm cấu hình backup trong `application.yml` (hoặc `config-repo-local`):
  - `backup.storage-dir: ${BACKUP_DIR:/backups}`
  - `backup.cron: "0 0 2 * * ?"`
  - `backup.retention-days: 30`
- [ ] **Enums:** Tạo `BackupType` (`AUTOMATIC`, `MANUAL`) và `BackupStatus` (`IN_PROGRESS`, `SUCCESS`, `FAILED`).
- [ ] **Entity:** Tạo entity `BackupHistory` kế thừa `BaseEntity` trong package `system.entity`.
- [ ] **Repository:** Tạo interface `BackupHistoryRepository` kế thừa `JpaRepository` với các hàm tìm kiếm theo status và ngày tạo.
- [ ] **DTOs:**
  - `BackupResponse`: Trả về thông tin bản sao lưu cho Admin UI.
- [ ] **Service:**
  - Interface `SystemBackupService` & Class `SystemBackupServiceImpl`.
  - Tích hợp Java `ProcessBuilder` gọi công cụ `mysqldump` & nén `GZIPOutputStream`.
  - Scheduled Cron Task với annotation `@Scheduled`.
- [ ] **Controller:** Tạo `SystemBackupController` cho nhóm endpoint `/api/system/backups`.
- [ ] **Verification:** Chạy kiểm tra biên dịch `.\gradlew compileJava`, chạy test `.\gradlew test` và build thử nghiệm `.\gradlew bootJar`.
