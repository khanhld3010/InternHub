# Specification: Xem Nhật Ký Hoạt Động Hệ Thống (Admin Activity Log & Audit Trail)

---

## 0. Revision History & Technical Rationale (Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật)

| Phiên bản | Ngày | Tác giả | Mã task Jira | Nội dung thay đổi | Lý do kỹ thuật / Nghiệp vụ (Rationale) |
| :---: | :---: | :---: | :---: | :--- | :--- |
| **v1.1** | 2026-09-24 | AI Pair-Programmer | TM-9 | Chuẩn hóa Role JWT và tối ưu route API Gateway | Sửa lỗi 403 Forbidden: vai trò `Admin` từ DB không khớp với `ROLE_ADMIN` của Spring Security do lệch chữ hoa/thường. Bổ sung chuẩn hóa in hoa trong `JwtAuthenticationFilter` và `JwtTokenProvider`, đồng thời cấu hình định tuyến kép tại API Gateway. |
| **v1.0** | 2026-09-21 | Đội Phát Triển | TM-9 | Khởi tạo tài liệu đặc tả nhật ký hoạt động hệ thống | Thiết kế ban đầu theo quy chuẩn Spec-Driven |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Xem nhật ký hoạt động và kiểm toán hệ thống (Admin Activity Log & Audit Trail Management)
- **Jira Ticket:** [TM-9](https://robluccibn9935.atlassian.net/browse/TM-9)
- **Target Subsystems:** 
  - `employee-service`: Backend ghi nhận nhật ký tự động qua Spring AOP/Events, cung cấp REST API truy vấn, thống kê và xuất dữ liệu.
  - `mysql-db`: Lưu trữ bảng `audit_logs` với các chỉ mục (indexes) tối ưu hóa truy vấn.
  - `api-gateway`: Định tuyến an toàn các endpoint `/api/system/audit-logs/**` về `employee-service`.
  - `internhub-frontend`: Màn hình Quản trị viên (Admin Dashboard) hiển thị danh sách nhật ký, bộ lọc đa tiêu chí, modal chi tiết, thống kê số liệu và **gọi 100% API Backend thực tế (loại bỏ hoàn toàn Mock API)**.
- **Target Users:**
  - 🛡️ **Quản trị viên (Admin):** Tra cứu lịch sử vận hành, giám sát các thao tác nhạy cảm, điều tra sự cố và kiểm tra tính tuân thủ an toàn thông tin.
  - ⚙️ **Hệ thống (System / Microservices):** Tự động ghi vết mọi hành vi trọng yếu mà không làm gián đoạn hoặc ảnh hưởng đến hiệu năng nghiệp vụ chính.
- **Phân loại thay đổi (Change Level):** **L3** (Thêm bảng cơ sở dữ liệu `audit_logs`, thiết lập cơ chế AOP Logging bất đồng bộ `@Async`, bổ sung bộ REST API mới và tái cấu trúc Frontend kết nối API thực).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Minh bạch hóa toàn bộ thao tác hệ thống (Audit Trail & Accountability):** Đảm bảo mọi thay đổi dữ liệu (tạo hồ sơ thực tập sinh, duyệt tài liệu, kích hoạt sao lưu dữ liệu, phân quyền tài khoản) đều được ghi nhận chi tiết: *Ai thực hiện? Vào lúc nào? Từ địa chỉ IP nào? Kết quả thành công hay thất bại?*.
2. **Hỗ trợ truy vết và điều tra sự cố (Security Forensics & Debugging):** Khi xảy ra lỗi phần mềm hoặc hành vi bất thường, Quản trị viên có đầy đủ thông tin chi tiết (request payload tóm tắt, stack trace lỗi, thời gian phản hồi) để khoanh vùng và xử lý nhanh chóng.
3. **Tuân thủ tiêu chuẩn an toàn thông tin (Security Compliance):** Đáp ứng các yêu cầu kiểm toán doanh nghiệp về lưu vết các thao tác quản trị và bảo vệ dữ liệu nhạy cảm thông qua cơ chế tự động che dấu thông tin (Data Masking).
4. **Loại bỏ hoàn toàn Mock API trên Frontend (Real API Integration):**
   - Chuyển đổi toàn bộ giao diện quản trị nhật ký hoạt động từ việc giả lập dữ liệu tĩnh (Mock Data) sang kết nối trực tiếp các REST API Backend thực tế qua API Gateway.
   - Đảm bảo giao diện hiển thị trạng thái chuẩn xác theo thời gian thực (Real-time DB Data), hỗ trợ phân trang động trên Server (`Pageable`), quản lý trạng thái tải (`Loading`), xử lý lỗi mạng (`Error Boundary / Toast`) và dữ liệu trống (`Empty State`).

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)

#### A. Backend (`employee-service` & `api-gateway`)
- **Thiết kế & Tạo bảng `audit_logs`:** Lưu trữ lịch sử hoạt động, chỉ mục composite index cho các trường thường xuyên lọc (`created_at`, `module`, `action`, `status`).
- **Cơ chế ghi log tự động không phong tỏa (Asynchronous Non-blocking Auditing):**
  - Sử dụng Custom Annotation `@Auditable` kết hợp Spring AOP (`AuditLogAspect`) và Spring Event / ThreadPool bất đồng bộ (`@Async`).
  - Ghi nhận tự động các hành vi trọng yếu trên hệ thống:
    - **Authentication:** `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`.
    - **Intern Profile:** `CREATE_INTERN`, `UPDATE_INTERN`, `CHANGE_INTERN_STATUS`.
    - **Document Management:** `UPLOAD_DOCUMENT`, `REVIEW_DOCUMENT`, `DOWNLOAD_DOCUMENT`.
    - **System Management:** `TRIGGER_BACKUP`, `DELETE_BACKUP`, `DOWNLOAD_BACKUP`, `TOGGLE_USER_STATUS`.
  - Thu thập ngữ cảnh HTTP an toàn: Client IP (xử lý `X-Forwarded-For`), HTTP Method, Endpoint URL, User Agent, Execution Time (ms).
  - Tự động lọc và che giấu dữ liệu nhạy cảm (Data Masking) trong request payload: mật khẩu, secret token, mã xác thực.
- **Bộ REST API Quản trị dành riêng cho Admin (`/api/system/audit-logs`):**
  - `GET /api/system/audit-logs`: Lấy danh sách nhật ký có phân trang, sắp xếp và lọc đa chiều (keyword, module, action, status, username, khoảng ngày `fromDate` -> `toDate`).
  - `GET /api/system/audit-logs/{id}`: Xem chi tiết một bản ghi nhật ký (kèm dữ liệu payload định dạng JSON và thông tin thiết bị/trình duyệt).
  - `GET /api/system/audit-logs/statistics`: Lấy thông số thống kê tổng quan (tổng log trong ngày, tỷ lệ thành công/thất bại, phân bổ hành động theo module).
  - `GET /api/system/audit-logs/export`: Xuất dữ liệu nhật ký ra tệp định dạng CSV phục vụ báo cáo.

#### B. Frontend (`internhub-frontend`)
- **Tích hợp màn hình Quản lý Nhật ký Hoạt động (Audit Logs Viewer):**
  - Bổ sung Tab / Phân vùng "Nhật ký hoạt động" trực quan trên trang Quản trị (`AdminDashboard.tsx`).
  - Hiển thị bảng dữ liệu hiện đại với các huy hiệu trạng thái (Badge: `SUCCESS`, `FAILED`), phân loại Module, thời gian theo giờ Việt Nam.
  - Thanh bộ lọc động: Tìm kiếm từ khóa (Keyword Debounce 400ms), Lọc theo Phân hệ (Module), Lọc theo Hành động (Action), Lọc theo Trạng thái (Status), Chọn khoảng thời gian (Từ ngày - Đến ngày).
  - Thẻ KPI thống kê nhanh: Tổng sự kiện hôm nay, Số sự kiện lỗi/cảnh báo, Hoạt động gần nhất.
  - Phân trang chuẩn Server-side: Chọn số lượng dòng/trang (10, 20, 50), chuyển trang Trước/Sau.
  - Modal xem chi tiết sự kiện: Trình bày chi tiết payload đã mask, IP máy trạm, User Agent, thời gian chạy và thông báo lỗi.
- **Loại bỏ Mock API (Real Backend Connection):**
  - Xây dựng `auditLogService.ts` sử dụng `apiClient` gọi trực tiếp các endpoint Backend thực tế.
  - **Tuyệt đối không sử dụng mảng mock tĩnh** hoặc fallback giả lập trong `auditLogService.ts`.
  - Cập nhật các service liên quan trên trang Admin (`userService.ts`) để đồng bộ gọi API thật hoặc xử lý lỗi chuẩn mực, không trả về dữ liệu giả lập gây nhầm lẫn.

---

### 3.2. Ngoài phạm vi (Out of Scope) - *Ngăn ngừa over-engineering*

- **Không cung cấp API Chỉnh sửa (`PUT/PATCH`) hoặc Xóa (`DELETE`) nhật ký:** Bảng `audit_logs` có tính chất bất biến (Append-Only / Immutable). Không bất kỳ ai, kể cả Admin, được phép xóa hoặc chỉnh sửa từng dòng nhật ký qua giao diện.
- **Không triển khai cụm ElasticSearch / Logstash / Kibana (ELK Stack):** Với quy mô hiện tại của hệ sinh thái InternHub, MySQL 8.0 với các chỉ mục B-Tree và phân trang Server-side đáp ứng hoàn hảo hiệu năng truy vấn cho hàng trăm nghìn bản ghi.
- **Không lưu toàn bộ file nhị phân (Binary stream) vào nhật ký:** Chỉ ghi metadata (tên file, kích thước, đuôi mở rộng) đối với các hành động upload/download tài liệu, không lưu nội dung nhị phân vào trường payload.

---

## 4. Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Edge Cases)

### 4.1. Case 1: Ghi log đồng bộ làm tăng độ trễ (Latency) và ảnh hưởng Transaction nghiệp vụ chính
- **Vấn đề:** Nếu quá trình ghi bản ghi vào bảng `audit_logs` thực hiện đồng bộ trong cùng một Database Transaction với hàm nghiệp vụ (ví dụ: tạo hồ sơ thực tập sinh), thời gian phản hồi API của người dùng sẽ bị cộng dồn thêm 50-100ms. Nguy hiểm hơn, nếu câu lệnh INSERT log bị lỗi, toàn bộ transaction tạo hồ sơ thực tập sinh của người dùng sẽ bị rollback oan uổng.
- **Giải pháp:**
  - Áp dụng cơ chế **Ghi log Bất đồng bộ (Asynchronous Event-Driven)** bằng cách kích hoạt Spring Application Event Publisher (`AuditLogEvent`) hoặc `@Async("auditLogExecutor")`.
  - Phương thức ghi log chạy trong Transaction độc lập: `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
  - Toàn bộ khối ghi log được bọc trong khối `try-catch`; nếu có lỗi xảy ra trong quá trình ghi log (ví dụ mất kết nối tạm thời), log lỗi sẽ được ghi ra console/file log của hệ thống (`log.error`) mà tuyệt đối không làm gián đoạn request của người dùng cuối.

### 4.2. Case 2: Lộ lọt dữ liệu nhạy cảm (Sensitive Information Leakage in Request Payload)
- **Vấn đề:** Khi ghi nhận request body của thao tác đăng nhập hoặc đổi mật khẩu, mật khẩu dạng thô (plaintext password) hoặc access token có thể bị lưu thẳng vào trường `request_payload` trong cơ sở dữ liệu, vi phạm nghiêm trọng an toàn thông tin.
- **Giải pháp:**
  - Xây dựng tiện ích làm sạch dữ liệu `DataMaskingUtils`.
  - Tự động quét và thay thế giá trị của các trường nhạy cảm (`password`, `currentPassword`, `newPassword`, `token`, `secretKey`, `authorization`) thành chuỗi `******` trước khi lưu trữ vào cột JSON payload.

### 4.3. Case 3: Tràn dung lượng bộ nhớ khi tìm kiếm và phân trang khối lượng dữ liệu lớn
- **Vấn đề:** Nếu người dùng yêu cầu phân trang với `size=1000000` hoặc thực hiện `COUNT(*)` trên hàng triệu bản ghi không có chỉ mục, máy chủ database có thể bị treo (high CPU/RAM).
- **Giải pháp:**
  - Giới hạn cứng kích thước trang: `size` tối đa là **100** bản ghi/trang. Nếu client gửi `size > 100`, hệ thống tự động gán về 100.
  - Thiết lập Composite Index: `(created_at DESC, module, status)` để câu lệnh sắp xếp và lọc theo ngày luôn tận dụng được Index Range Scan thay vì Full Table Scan.
  - Sử dụng truy vấn phân trang chuẩn `Pageable` của Spring Data JPA kết hợp `JpaSpecificationExecutor`.

### 4.4. Case 4: Nhận diện sai lệch Client IP khi ứng dụng nằm sau API Gateway / Reverse Proxy
- **Vấn đề:** Khi người dùng gửi request qua API Gateway (hoặc Nginx), phương thức `request.getRemoteAddr()` của servlet container thường chỉ trả về địa chỉ IP nội bộ của container Gateway (ví dụ `172.18.0.x`), làm mất đi địa chỉ IP thực sự của người dùng.
- **Giải pháp:**
  - Viết hàm tiện ích `IpUtils.getClientIp(HttpServletRequest request)` kiểm tra tuần tự các header HTTP:
    1. `X-Forwarded-For` (lấy địa chỉ IP đầu tiên trong danh sách dấu phẩy).
    2. `X-Real-IP`.
    3. `Proxy-Client-IP`.
    4. Fallback về `request.getRemoteAddr()`.

### 4.5. Case 5: Frontend hiểu lầm trạng thái hệ thống do cơ chế Fallback Mock âm thầm
- **Vấn đề:** Trong các phiên bản trước, khi API Backend bị lỗi mạng (Network Error) hoặc trả về 500, một số service frontend âm thầm `catch` lỗi và trả về dữ liệu mẫu (`mockData`). Điều này khiến Admin tưởng rằng hệ thống đang hoạt động bình thường với dữ liệu thật, gây sai lệch thông tin kiểm toán nghiêm trọng.
- **Giải pháp:**
  - **Xóa bỏ hoàn toàn cơ chế Fallback Mock trong `auditLogService.ts`**.
  - Khi Backend trả lỗi hoặc không phản hồi, `auditLogService` phải ném lỗi ra ngoài (`throw error`).
  - Giao diện AdminDashboard bắt lỗi, dừng trạng thái `loading` và hiển thị thông báo lỗi rõ ràng (Alert/Toast): *"Không thể kết nối đến máy chủ để lấy nhật ký hoạt động. Vui lòng kiểm tra lại dịch vụ Backend!"*, đồng thời cung cấp nút "Thử lại" (Retry).

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Tự động ghi nhận nhật ký - Automated Event Auditing):** Mọi hành vi kích hoạt trên các endpoint trọng yếu có gắn annotation `@Auditable` phải được tự động ghi nhận vào bảng `audit_logs` đầy đủ các thông tin: người thực hiện, vai trò, hành động, phân hệ, endpoint, IP máy trạm, trạng thái (`SUCCESS`/`FAILED`), thời gian thực thi (ms).
- **FR-2 (Truy vấn danh sách phân trang - Paged Audit Log Retrieval):** Cung cấp API cho phép Quản trị viên truy vấn danh sách nhật ký theo cấu trúc phân trang (`content`, `pageNumber`, `pageSize`, `totalElements`, `totalPages`), mặc định sắp xếp theo thời gian mới nhất (`createdAt DESC`).
- **FR-3 (Lọc dữ liệu đa tiêu chí - Multi-criteria Filtering):** Cho phép tìm kiếm và lọc kết hợp:
  - Từ khóa tìm kiếm tự do (`keyword`): khớp với `username`, `description`, `endpoint`.
  - Phân hệ (`module`): `AUTH`, `INTERN`, `DOCUMENT`, `SYSTEM`, `USER`.
  - Hành động (`action`): `LOGIN`, `CREATE_INTERN`, `UPDATE_INTERN`, `REVIEW_DOCUMENT`, `TRIGGER_BACKUP`, v.v.
  - Trạng thái (`status`): `SUCCESS`, `FAILED`.
  - Người thực hiện (`username`).
  - Khoảng thời gian: `fromDate` và `toDate` (định dạng `YYYY-MM-DD` hoặc ISO-8601).
- **FR-4 (Xem chi tiết bản ghi nhật ký - Audit Event Detail):** Cho phép Admin xem toàn bộ thông tin chuyên sâu của một sự kiện: Tên thiết bị/trình duyệt (`User-Agent`), Request Payload (đã che mật khẩu), nguyên nhân lỗi chi tiết (`errorMessage` / Stack trace nếu thất bại).
- **FR-5 (Thống kê hoạt động hệ thống - Audit Statistics):** Cung cấp API trả về các chỉ số thống kê nhanh:
  - Tổng số thao tác ghi nhận trong ngày hôm nay.
  - Số lượng thao tác thành công và số lượng thao tác thất bại.
  - Phân bổ số lượng theo từng phân hệ (`module`).
- **FR-6 (Xuất dữ liệu kiểm toán CSV - Audit Log CSV Export):** Cho phép Quản trị viên tải xuống tệp danh sách nhật ký dưới định dạng `.csv` (mã hóa UTF-8 kèm BOM để hiển thị đúng tiếng Việt trên Microsoft Excel).
- **FR-7 (Giao diện Frontend thời gian thực - Real API Frontend UI):**
  - Màn hình Quản trị Admin tích hợp tab "Nhật ký hoạt động" với đầy đủ bảng dữ liệu, thanh tìm kiếm từ khóa, bộ lọc module/action/status, date-range picker.
  - Modal xem chi tiết sự kiện với giao diện JSON đẹp mắt.
  - Kết nối trực tiếp đến API Gateway `/api/system/audit-logs`, loại bỏ hoàn toàn Mock API và Mock Data.
- **FR-8 (Phân quyền truy cập nghiêm ngặt - Role-Based Access Control):** Chỉ tài khoản có vai trò `ADMIN` mới có quyền truy cập vào các API và màn hình nhật ký hoạt động. Người dùng vai trò khác (`HR`, `MENTOR`, `INTERN`) nhận mã lỗi `403 Forbidden` nếu cố tình truy cập.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Quyền hạn truy cập):** Tất cả các endpoint thuộc nhóm `/api/system/audit-logs/**` bắt buộc phải có JWT Token hợp lệ và người dùng phải mang vai trò `ROLE_ADMIN`.
- **BR-2 (Tính bất biến - Append-Only Immutability):** Bảng `audit_logs` là bảng chỉ đọc và thêm mới (Append-Only). Hệ thống tuyệt đối không cung cấp bất kỳ API nào để sửa (`UPDATE`) hoặc xóa (`DELETE`) bản ghi nhật ký.
- **BR-3 (Bảo vệ thông tin bí mật - Sensitive Data Masking):** Bất kỳ dữ liệu nào gửi lên chứa các khóa nhạy cảm (`password`, `newPassword`, `token`, `secret`, `accessToken`) đều phải được tự động làm mờ thành `******` trước khi ghi nhận vào trường `requestPayload`.
- **BR-4 (Độc lập giao dịch - Transaction Independence):** Tiến trình ghi nhật ký hoạt động phải chạy bất đồng bộ và hoàn toàn tách biệt khỏi luồng giao dịch của chức năng nghiệp vụ chính. Lỗi ghi audit log không được phép làm thất bại nghiệp vụ của người dùng.
- **BR-5 (Giới hạn phân trang an toàn):** Kích thước trang mặc định là **20** bản ghi; kích thước trang tối đa không được vượt quá **100** bản ghi.
- **BR-6 (Chuẩn hóa múi giờ):** Dữ liệu lưu trong cơ sở dữ liệu luôn sử dụng múi giờ UTC (`TIMESTAMP`). Khi trả về Client qua API sử dụng định dạng ISO-8601 (`YYYY-MM-DDTHH:mm:ssZ`). Giao diện Frontend có trách nhiệm hiển thị sang giờ địa phương của Quản trị viên (`Asia/Ho_Chi_Minh` - GMT+7).
- **BR-7 (Chính sách không Mock trên Frontend):** Toàn bộ các yêu cầu lấy dữ liệu kiểm toán trên giao diện người dùng phải được chuyển trực tiếp tới Backend qua HTTP Client (`apiClient`). Khi Backend không phản hồi hoặc trả lỗi, hệ thống phải thông báo trạng thái lỗi rõ ràng, không được phép hiển thị dữ liệu giả định.

---

## 7. Data Model (Mô Hình Dữ Liệu)

Kế thừa `BaseEntity` (`id` BIGINT AUTO_INCREMENT, `created_at` DATETIME, `updated_at` DATETIME).

### 7.1. Bảng `audit_logs` (DDL SQL)
```sql
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,                             -- ID người dùng (NULL nếu hệ thống hoặc chưa đăng nhập)
    username VARCHAR(100) NOT NULL DEFAULT 'SYSTEM', -- Tên tài khoản thực hiện thao tác
    user_role VARCHAR(50) NULL,                      -- 'ADMIN', 'HR', 'MENTOR', 'INTERN', 'ANONYMOUS'
    action VARCHAR(50) NOT NULL,                     -- Mã hành động (ví dụ: 'LOGIN_SUCCESS', 'CREATE_INTERN')
    module VARCHAR(50) NOT NULL,                     -- Phân hệ ('AUTH', 'INTERN', 'DOCUMENT', 'SYSTEM', 'USER')
    description VARCHAR(500) NOT NULL,               -- Mô tả tóm tắt hành động bằng tiếng Việt
    endpoint VARCHAR(255) NOT NULL,                  -- Đường dẫn API (ví dụ: '/api/employees/interns')
    http_method VARCHAR(10) NOT NULL,                -- 'GET', 'POST', 'PUT', 'PATCH', 'DELETE'
    client_ip VARCHAR(50) NULL,                      -- Địa chỉ IP của máy trạm
    user_agent VARCHAR(500) NULL,                    -- Thông tin trình duyệt và hệ điều hành
    status VARCHAR(20) NOT NULL,                     -- Trạng thái: 'SUCCESS', 'FAILED'
    execution_time_ms BIGINT NULL DEFAULT 0,         -- Thời gian thực thi API (mili-giây)
    error_message TEXT NULL,                         -- Chi tiết lỗi hoặc stack trace tóm tắt nếu thất bại
    request_payload JSON NULL,                       -- Dữ liệu request body (đã mask thông tin nhạy cảm)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Chỉ mục tối ưu hóa tìm kiếm & lọc
    INDEX idx_audit_created_at (created_at DESC),
    INDEX idx_audit_module (module),
    INDEX idx_audit_action (action),
    INDEX idx_audit_status (status),
    INDEX idx_audit_username (username),
    INDEX idx_audit_composite (created_at DESC, module, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 7.2. Các Enum nghiệp vụ

#### `AuditModule`
```java
package org.example.employeeservice.system.entity;

public enum AuditModule {
    AUTH,       // Xác thực, đăng nhập, phân quyền
    INTERN,     // Quản lý hồ sơ thực tập sinh
    DOCUMENT,   // Quản lý hồ sơ, tài liệu, CV
    SYSTEM,     // Sao lưu hệ thống, cấu hình nền tảng
    USER        // Quản lý tài khoản người dùng
}
```

#### `AuditAction`
```java
package org.example.employeeservice.system.entity;

public enum AuditAction {
    // Auth actions
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    
    // Intern actions
    CREATE_INTERN,
    UPDATE_INTERN,
    CHANGE_INTERN_STATUS,
    
    // Document actions
    UPLOAD_DOCUMENT,
    REVIEW_DOCUMENT,
    DOWNLOAD_DOCUMENT,
    
    // System actions
    TRIGGER_BACKUP,
    DELETE_BACKUP,
    DOWNLOAD_BACKUP,
    
    // User actions
    TOGGLE_USER_STATUS,
    CREATE_USER,
    UPDATE_USER
}
```

#### `AuditStatus`
```java
package org.example.employeeservice.system.entity;

public enum AuditStatus {
    SUCCESS,
    FAILED
}
```

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

- **Base URL:** `/api/system/audit-logs`
- **Security:** Bắt buộc Header `Authorization: Bearer <ADMIN_JWT_TOKEN>`

### 8.1. Lấy danh sách nhật ký hoạt động (Paged & Filtered)
- **Method:** `GET`
- **Path:** `/api/system/audit-logs`
- **Query Parameters:**
  - `keyword` (String, optional): Tìm kiếm theo username, mô tả hoặc endpoint.
  - `module` (String, optional): `AUTH`, `INTERN`, `DOCUMENT`, `SYSTEM`, `USER`.
  - `action` (String, optional): Tên hành động (`AuditAction`).
  - `status` (String, optional): `SUCCESS`, `FAILED`.
  - `username` (String, optional): Lọc đích danh người thực hiện.
  - `fromDate` (String, optional): Từ ngày (`YYYY-MM-DD` hoặc `YYYY-MM-DDTHH:mm:ss`).
  - `toDate` (String, optional): Đến ngày (`YYYY-MM-DD` hoặc `YYYY-MM-DDTHH:mm:ss`).
  - `page` (Integer, default `0`): Chỉ số trang (bắt đầu từ 0).
  - `size` (Integer, default `20`, max `100`): Kích thước mỗi trang.
  - `sort` (String, default `createdAt,desc`): Thuộc tính sắp xếp.

#### Response: `200 OK`
```json
{
  "code": 200,
  "message": "Lấy danh sách nhật ký hoạt động thành công",
  "data": {
    "content": [
      {
        "id": 1054,
        "userId": 1,
        "username": "admin@internhub.vn",
        "userRole": "ADMIN",
        "action": "TRIGGER_BACKUP",
        "module": "SYSTEM",
        "description": "Kích hoạt sao lưu dữ liệu thủ công tức thời",
        "endpoint": "/api/system/backups",
        "httpMethod": "POST",
        "clientIp": "192.168.1.45",
        "status": "SUCCESS",
        "executionTimeMs": 320,
        "createdAt": "2026-09-21T10:15:00Z"
      },
      {
        "id": 1053,
        "userId": 2,
        "username": "hr@internhub.vn",
        "userRole": "HR",
        "action": "REVIEW_DOCUMENT",
        "module": "DOCUMENT",
        "description": "Xét duyệt hồ sơ tài liệu ID=102: Phê duyệt (APPROVED)",
        "endpoint": "/api/employees/interns/documents/102/review",
        "httpMethod": "PATCH",
        "clientIp": "118.69.12.8",
        "status": "SUCCESS",
        "executionTimeMs": 45,
        "createdAt": "2026-09-21T09:40:12Z"
      },
      {
        "id": 1052,
        "userId": null,
        "username": "unknown@domain.com",
        "userRole": "ANONYMOUS",
        "action": "LOGIN_FAILED",
        "module": "AUTH",
        "description": "Đăng nhập thất bại: Sai thông tin xác thực",
        "endpoint": "/api/auth/login",
        "httpMethod": "POST",
        "clientIp": "14.232.18.99",
        "status": "FAILED",
        "executionTimeMs": 110,
        "createdAt": "2026-09-21T09:12:05Z"
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 348,
    "totalPages": 18,
    "last": false
  },
  "timestamp": "2026-09-21T10:15:30Z"
}
```

---

### 8.2. Lấy thông tin chi tiết một bản ghi nhật ký
- **Method:** `GET`
- **Path:** `/api/system/audit-logs/{id}`

#### Response: `200 OK`
```json
{
  "code": 200,
  "message": "Lấy thông tin chi tiết nhật ký thành công",
  "data": {
    "id": 1054,
    "userId": 1,
    "username": "admin@internhub.vn",
    "userRole": "ADMIN",
    "action": "TRIGGER_BACKUP",
    "module": "SYSTEM",
    "description": "Kích hoạt sao lưu dữ liệu thủ công tức thời",
    "endpoint": "/api/system/backups",
    "httpMethod": "POST",
    "clientIp": "192.168.1.45",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
    "status": "SUCCESS",
    "executionTimeMs": 320,
    "errorMessage": null,
    "requestPayload": {
      "type": "MANUAL",
      "note": "Backup trước khi nâng cấp phiên bản hệ thống"
    },
    "createdAt": "2026-09-21T10:15:00Z"
  },
  "timestamp": "2026-09-21T10:16:00Z"
}
```

#### Response Lỗi: `404 Not Found`
```json
{
  "code": 404,
  "message": "Không tìm thấy bản ghi nhật ký hoạt động với ID: 9999",
  "data": null,
  "timestamp": "2026-09-21T10:16:15Z"
}
```

---

### 8.3. Thống kê tổng hợp số liệu hoạt động (Audit Statistics)
- **Method:** `GET`
- **Path:** `/api/system/audit-logs/statistics`

#### Response: `200 OK`
```json
{
  "code": 200,
  "message": "Lấy dữ liệu thống kê nhật ký thành công",
  "data": {
    "totalToday": 142,
    "totalSuccess": 135,
    "totalFailed": 7,
    "successRate": 95.07,
    "moduleBreakdown": {
      "AUTH": 45,
      "INTERN": 52,
      "DOCUMENT": 30,
      "SYSTEM": 10,
      "USER": 5
    }
  },
  "timestamp": "2026-09-21T10:20:00Z"
}
```

---

### 8.4. Xuất dữ liệu nhật ký dạng CSV (Export CSV)
- **Method:** `GET`
- **Path:** `/api/system/audit-logs/export`
- **Query Parameters:** Các bộ lọc tương tự như API danh sách (`keyword`, `module`, `action`, `status`, `fromDate`, `toDate`).
- **Response Headers:**
  - `Content-Type: text/csv; charset=UTF-8`
  - `Content-Disposition: attachment; filename="audit_logs_20260921_102500.csv"`
- **Response Body:** Nội dung dạng file CSV (có chứa UTF-8 BOM `\uFEFF`):
```csv
ID,Thời Gian,Người Thực Hiện,Vai Trò,Phân Hệ,Hành Động,Mô Tả,Endpoint,Phương Thức,Địa Chỉ IP,Trạng Thái,Thời Gian Xử Lý (ms)
1054,2026-09-21 10:15:00,admin@internhub.vn,ADMIN,SYSTEM,TRIGGER_BACKUP,"Kích hoạt sao lưu dữ liệu thủ công tức thời",/api/system/backups,POST,192.168.1.45,SUCCESS,320
1053,2026-09-21 09:40:12,hr@internhub.vn,HR,DOCUMENT,REVIEW_DOCUMENT,"Xét duyệt hồ sơ tài liệu ID=102: Phê duyệt (APPROVED)",/api/employees/interns/documents/102/review,PATCH,118.69.12.8,SUCCESS,45
```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)

### 9.1. Luồng Ghi nhận Nhật ký Hoạt động qua Spring AOP (`@Auditable`)

```
[Client Request] ──(Gửi HTTP Request kèm Bearer Token)──► [API Gateway :8080]
                                                                  │
                                                                  ▼
                                                      [Employee Service :8081]
                                                                  │
                                            ┌─────────────────────┴──────────────────────┐
                                            ▼                                            ▼
                                  [AuditLogAspect] (Around)                    [Controller Method]
                                  - Bắt đầu đo thời gian (stopwatch)                     │
                                  - Đọc Annotation @Auditable                            │
                                  - Trích xuất Client IP, Method, URL                    ▼
                                  - Bắt và thực thi Controller ──────────────► [Service Business Logic]
                                            │                                            │
                                            │ (Nhận kết quả hoặc Exception)              ▼
                                            ▼                                     [DB Transaction]
                                  - Xác định Status: SUCCESS / FAILED                    │
                                  - Làm sạch Payload (Mask Password)                     │
                                  - Đóng gói AuditLogEvent                               │
                                            │                                            ▼
                                            ▼                                  [Response cho Client]
                                  [Spring Event Publisher]
                                            │ (Bất đồng bộ @Async)
                                            ▼
                                  [AuditLogEventListener]
                                            │ (Transaction REQUIRES_NEW)
                                            ▼
                                   INSERT INTO audit_logs
```

---

### 9.2. Luồng Frontend Tải Dữ Liệu Thực Tế (Không Dùng Mock)

```
[Quản trị viên mở Tab Nhật Ký]
           │
           ▼
[AdminDashboard Component] ──(Gọi hàm loadAuditLogs())──► [auditLogService.getAuditLogs(filterParams)]
                                                                           │
                                                                           ▼ (Axios apiClient)
                                                             [GET /api/system/audit-logs?params]
                                                                           │
                                    ┌──────────────────────────────────────┴──────────────────────────────────────┐
                                    ▼ (Thành công: HTTP 200)                                                      ▼ (Thất bại: HTTP 401/403/500/Network Error)
                     [Trích xuất response.data.data]                                                [Bắt lỗi catch(error)]
                                    │                                                                             │
                                    ▼                                                                             ▼
                    - Cập nhật state `logs` = content                                             - Tắt loading: `setLoading(false)`
                    - Cập nhật state `totalPages`, `totalElements`                                - Hiển thị Banner lỗi / Toast thông báo
                    - Cập nhật state `statistics`                                                 - Tuyệt đối KHÔNG gán mockLogs vào state
                    - Tắt loading: `setLoading(false)`                                            - Cho phép Admin nhấn nút "Thử lại"
                                    │
                                    ▼
                    [Render Bảng Dữ Liệu Thực Tế]
```

---

## 10. Non-Functional Requirements & Constraints (Yêu Cầu Phi Chức Năng)

1. **Hiệu năng & Bất đồng bộ (Performance & Async Non-blocking):**
   - Tác vụ ghi nhận nhật ký phải chạy trên một Thread Pool riêng (`auditLogExecutor`) với kích thước phù hợp (`corePoolSize=4`, `maxPoolSize=16`, `queueCapacity=500`).
   - Thời gian đáp ứng của luồng nghiệp vụ chính tăng thêm tối đa không quá **5ms**.
2. **Bảo mật & Phân quyền (Security & RBAC):**
   - Chỉ tài khoản có Role `ADMIN` mới được phép gọi các API `/api/system/audit-logs/**`.
   - Mật khẩu, token, và các trường nhạy cảm phải được mask triệt để trước khi ghi vào cơ sở dữ liệu.
3. **Tính toàn vẹn & Bất biến (Immutability):**
   - Nhật ký kiểm toán không thể bị sửa hoặc xóa qua API hoặc giao diện.
4. **Quy chuẩn Frontend (Zero Mock Policy):**
   - Không chứa bất kỳ mảng dữ liệu giả lập (mock data fallback) nào trong file `auditLogService.ts`.
   - Kết nối 100% đến các endpoint thật của Backend thông qua API Gateway.
   - Hiển thị đầy đủ trạng thái Loading skeleton, Empty state, và Error message rõ ràng.
5. **Chuẩn hóa Quốc tế hóa & Múi giờ:**
   - Ngày giờ trả về từ backend định dạng chuẩn ISO-8601 UTC.
   - Frontend định dạng hiển thị tiếng Việt (`vi-VN`) với đầy đủ ngày tháng năm và giờ phút giây.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [ ] **AC-1 (Ghi log tự động khi thực hiện hành vi):** Khi người dùng thực hiện một hành động (ví dụ: Admin kích hoạt backup, HR duyệt hồ sơ thực tập sinh), một bản ghi mới được tự động chèn vào bảng `audit_logs` với đầy đủ username, action, module, status `SUCCESS`.
- [ ] **AC-2 (Ghi log khi thất bại):** Khi có một hành động gây ra lỗi (ví dụ đăng nhập sai mật khẩu), bản ghi audit log được ghi lại với status `FAILED` và trường `errorMessage` chứa thông tin lỗi tóm tắt.
- [ ] **AC-3 (Làm mờ dữ liệu nhạy cảm):** Bản ghi nhật ký của hành động đăng nhập (`LOGIN`) hoặc đổi mật khẩu tuyệt đối không chứa mật khẩu thô trong cột `request_payload` (phải là `******`).
- [ ] **AC-4 (API phân trang & lọc động):** Gọi `GET /api/system/audit-logs` với các tham số `keyword`, `module`, `status`, `page`, `size` trả về đúng danh sách dữ liệu được lọc và số trang tương ứng.
- [ ] **AC-5 (API chi tiết nhật ký):** Gọi `GET /api/system/audit-logs/{id}` trả về đầy đủ thông tin chi tiết của sự kiện bao gồm `userAgent`, `clientIp` và `requestPayload`. Nếu ID không tồn tại, trả về `404 Not Found`.
- [ ] **AC-6 (API thống kê):** Gọi `GET /api/system/audit-logs/statistics` trả về đúng tổng số lượng log hôm nay, số lượng thành công, số lượng thất bại.
- [ ] **AC-7 (API xuất CSV):** Gọi `GET /api/system/audit-logs/export` tải về tệp `.csv` chứa danh sách nhật ký, mở được tiếng Việt có dấu chuẩn xác trên Excel.
- [ ] **AC-8 (Bảo mật Admin-only):** Người dùng không có vai trò `ADMIN` khi gọi bất kỳ endpoint nào thuộc `/api/system/audit-logs/**` đều nhận về mã lỗi `403 Forbidden`.
- [ ] **AC-9 (Frontend kết nối API thực tế):** Giao diện Admin Dashboard hiển thị danh sách nhật ký lấy từ Backend. Khi tải lại trang hoặc đổi bộ lọc, Frontend gửi request thật tới API Gateway thay vì dùng dữ liệu tĩnh trong `mockData.ts`.
- [ ] **AC-10 (Frontend xử lý lỗi mạng chuẩn mực):** Khi tắt Backend hoặc ngắt mạng, giao diện Admin hiển thị thông báo lỗi rõ ràng, không âm thầm nạp mock data để đánh lừa người dùng.

---

## 12. Unit & Integration Test Cases Checklist

### 12.1. Backend Unit Tests (`employee-service`)
- [ ] `UT-BE-01`: `DataMaskingUtilsTest` - Kiểm tra hàm mask dữ liệu làm mờ thành công các trường `password`, `token` trong chuỗi JSON.
- [ ] `UT-BE-02`: `IpUtilsTest` - Kiểm tra trích xuất đúng Client IP từ header `X-Forwarded-For`.
- [ ] `UT-BE-03`: `AuditLogServiceTest.getAuditLogs_shouldReturnPagedData()` - Kiểm tra truy vấn danh sách phân trang với JPA Specification.
- [ ] `UT-BE-04`: `AuditLogServiceTest.getAuditLogDetail_whenIdExists_shouldReturnDetail()` - Trả về chi tiết khi tìm thấy bản ghi.
- [ ] `UT-BE-05`: `AuditLogServiceTest.getAuditLogDetail_whenIdNotFound_shouldThrowResourceNotFoundException()` - Ném ngoại lệ khi không tìm thấy ID.
- [ ] `UT-BE-06`: `AuditLogAspectTest` - Kiểm tra Aspect bắt đúng method gắn `@Auditable` và phát event `AuditLogEvent`.

### 12.2. Backend Integration Tests (`employee-service`)
- [ ] `IT-BE-01`: `GET /api/system/audit-logs` với quyền `ADMIN` $\rightarrow$ Trả về `200 OK` với dữ liệu phân trang chuẩn.
- [ ] `IT-BE-02`: `GET /api/system/audit-logs` với quyền `HR` hoặc `INTERN` $\rightarrow$ Trả về `403 Forbidden`.
- [ ] `IT-BE-03`: `GET /api/system/audit-logs` không kèm Token $\rightarrow$ Trả về `401 Unauthorized`.
- [ ] `IT-BE-04`: `GET /api/system/audit-logs/{id}` $\rightarrow$ Trả về `200 OK` kèm thông tin payload.
- [ ] `IT-BE-05`: `GET /api/system/audit-logs/statistics` $\rightarrow$ Trả về `200 OK` kèm các chỉ số thống kê.
- [ ] `IT-BE-06`: `GET /api/system/audit-logs/export` $\rightarrow$ Trả về `200 OK` với header `Content-Type: text/csv`.

### 12.3. Frontend Unit & Integration Tests (`internhub-frontend`)
- [ ] `UT-FE-01`: `auditLogService.getAuditLogs()` gửi đúng URL và query parameters tới API Gateway.
- [ ] `UT-FE-02`: `auditLogService` ném lỗi khi API trả về 500 (không fallback về mảng mock).
- [ ] `UT-FE-03`: `AdminDashboard` render bảng nhật ký với các badge trạng thái tương ứng dữ liệu trả về từ API.
- [ ] `UT-FE-04`: Khi chuyển trang hoặc thay đổi bộ lọc, hàm gọi API được kích hoạt lại với tham số tương ứng.

---

## 13. Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai)

### 13.1. Backend (`employee-service`)
- [ ] **Annotation & AOP:**
  - `system/audit/annotation/Auditable.java`: Custom annotation đánh dấu các phương thức cần ghi vết.
  - `system/audit/aspect/AuditLogAspect.java`: Spring Aspect đo đạc thời gian, thu thập IP, Payload và phát event.
- [ ] **Event & Async Handling:**
  - `system/audit/event/AuditLogEvent.java`: DTO Event chứa dữ liệu nhật ký.
  - `system/audit/event/AuditLogEventListener.java`: Listener bất đồng bộ (`@Async`) tiếp nhận event và lưu xuống DB.
- [ ] **Utils:**
  - `system/audit/util/DataMaskingUtils.java`: Tiện ích che giấu trường nhạy cảm.
  - `system/audit/util/IpUtils.java`: Tiện ích trích xuất IP Client thực tế.
- [ ] **Entity & Enums:**
  - `system/audit/entity/AuditLog.java`: JPA Entity kế thừa `BaseEntity`.
  - `system/audit/entity/AuditModule.java`: Enum phân hệ.
  - `system/audit/entity/AuditAction.java`: Enum hành động.
  - `system/audit/entity/AuditStatus.java`: Enum trạng thái.
- [ ] **Repository & Specification:**
  - `system/audit/repository/AuditLogRepository.java`: Kế thừa `JpaRepository` & `JpaSpecificationExecutor`.
  - `system/audit/repository/AuditLogSpecification.java`: Xây dựng Predicate lọc linh hoạt theo keyword, module, action, status, date range.
- [ ] **DTOs:**
  - `system/audit/dto/request/AuditLogFilterRequest.java`: DTO nhận tham số query filter.
  - `system/audit/dto/response/AuditLogResponse.java`: DTO tóm tắt danh sách.
  - `system/audit/dto/response/AuditLogDetailResponse.java`: DTO chi tiết kèm payload và user agent.
  - `system/audit/dto/response/AuditLogStatsResponse.java`: DTO thống kê số liệu.
- [ ] **Service & Implementation:**
  - `system/audit/service/AuditLogService.java`: Interface nghiệp vụ.
  - `system/audit/service/impl/AuditLogServiceImpl.java`: Logic truy vấn, thống kê, export CSV và lưu log.
- [ ] **Controller:**
  - `system/audit/controller/AuditLogController.java`: Khai báo 4 REST endpoint `/api/system/audit-logs/**` với `@PreAuthorize("hasRole('ADMIN')")`.
- [ ] **Áp dụng `@Auditable` vào các hàm trọng yếu:**
  - `AuthController.java`: Login / Logout.
  - `InternController.java`: Create / Update Intern.
  - `DocumentController.java`: Upload / Review Document.
  - `SystemBackupController.java`: Trigger / Delete / Download Backup.

### 13.2. Frontend (`internhub-frontend`)
- [ ] **Types:**
  - Bổ sung các interface trong `src/types/index.ts`: `AuditModule`, `AuditAction`, `AuditStatus`, `AuditLogItem`, `AuditLogDetail`, `AuditLogStats`, `AuditLogFilterParams`.
- [ ] **Service:**
  - Tạo mới `src/services/auditLogService.ts`: Triển khai các hàm `getAuditLogs`, `getAuditLogById`, `getStatistics`, `exportAuditLogsCsv`. **Tuyệt đối không sử dụng mock data fallback**.
  - Kiểm tra và tinh chỉnh `src/services/userService.ts` để đảm bảo luồng xử lý lỗi chuẩn xác khi gọi API thật.
- [ ] **Giao diện Admin (`AdminDashboard.tsx`):**
  - Thêm Tab chuyển đổi: `Tài khoản & Phân quyền` | `Sao lưu dữ liệu (TM-8)` | `Nhật ký hoạt động (TM-9)`.
  - Thiết kế bảng hiển thị nhật ký chuyên nghiệp: hiển thị Action, Module, Người thực hiện, IP, Trạng thái, Thời gian thực hiện.
  - Tích hợp thanh công cụ lọc: Input Search (Debounced), Select Box Module, Select Box Trạng thái, Bộ chọn khoảng ngày.
  - Tích hợp Thẻ KPI Thống kê (Tổng log hôm nay, Tỷ lệ thành công, v.v.).
  - Nút xuất dữ liệu ra file CSV (`Xuất CSV`).
  - Modal xem chi tiết sự kiện: Trình bày chi tiết JSON payload đã mask, IP, trình duyệt.
  - Xử lý trạng thái Loading (Spinner), Dữ liệu trống (Empty message), Lỗi kết nối (Error Alert kèm nút Retry).
- [ ] **Verification:**
  - Biên dịch Backend: `cd employee-service; .\gradlew compileJava; cd ..`
  - Chạy test Backend: `cd employee-service; .\gradlew test; cd ..`
  - Build Frontend: `cd internhub-frontend; npm run build; cd ..`
