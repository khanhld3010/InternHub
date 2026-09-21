# Task List: TM-9 Xem Nhật Ký Hoạt Động Hệ Thống (Audit Log) & Tích Hợp Frontend API Thật

---

## Danh Sách Task Triển Khai Chi Tiết & Độc Lập

### 🟢 GIAI ĐOẠN 1: XÂY DỰNG NỀN TẢNG AUDIT LOG BACKEND (`employee-service`)

- [x] **Task 1: Khởi tạo Enums & JPA Entity `AuditLog`**
  - [x] Tạo Enum `AuditModule` (`AUTH`, `INTERN`, `DOCUMENT`, `SYSTEM`, `USER`) tại `system/audit/entity/AuditModule.java`.
  - [x] Tạo Enum `AuditAction` (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `CREATE_INTERN`, `UPDATE_INTERN`, `CHANGE_INTERN_STATUS`, `UPLOAD_DOCUMENT`, `REVIEW_DOCUMENT`, `DOWNLOAD_DOCUMENT`, `TRIGGER_BACKUP`, `DELETE_BACKUP`, `DOWNLOAD_BACKUP`, `TOGGLE_USER_STATUS`) tại `system/audit/entity/AuditAction.java`.
  - [x] Tạo Enum `AuditStatus` (`SUCCESS`, `FAILED`) tại `system/audit/entity/AuditStatus.java`.
  - [x] Tạo JPA Entity `AuditLog` kế thừa `BaseEntity` với đầy đủ các trường: `userId`, `username`, `userRole`, `action`, `module`, `description`, `endpoint`, `httpMethod`, `clientIp`, `userAgent`, `status`, `executionTimeMs`, `errorMessage`, `requestPayload` (cột JSON).

- [x] **Task 2: Tạo Repository & Dynamic Specification (`AuditLogSpecification`)**
  - [x] Tạo interface `AuditLogRepository` kế thừa `JpaRepository<AuditLog, Long>` và `JpaSpecificationExecutor<AuditLog>`.
  - [x] Tạo class `AuditLogSpecification` để xây dựng Predicate truy vấn động:
    - Tìm kiếm theo từ khóa `keyword` (tìm trong `username`, `description`, `endpoint`).
    - Lọc chính xác theo `module`, `action`, `status`, `username`.
    - Lọc theo khoảng ngày `fromDate` và `toDate`.
  - [x] Thêm các method thống kê nhanh trong Repository: đếm log theo khoảng ngày, đếm theo trạng thái, đếm theo module.

- [x] **Task 3: Xây dựng Request & Response DTOs**
  - [x] Tạo `AuditLogFilterRequest` tiếp nhận tham số query từ client (`keyword`, `module`, `action`, `status`, `username`, `fromDate`, `toDate`, `page`, `size`, `sort`).
  - [x] Tạo `AuditLogResponse` trả về dữ liệu tóm tắt danh sách.
  - [x] Tạo `AuditLogDetailResponse` trả về dữ liệu chi tiết sự kiện kèm User-Agent và Payload JSON.
  - [x] Tạo `AuditLogStatsResponse` trả về số liệu thống kê tổng hợp (tổng hôm nay, thành công, thất bại, phân bổ module).

- [x] **Task 4: Xây dựng Tiện Ích An Toàn Dữ Liệu & HTTP Context**
  - [x] Tạo `DataMaskingUtils`: Tự động tìm và làm mờ các trường nhạy cảm (`password`, `token`, `secret`, `currentPassword`, `newPassword`) thành `******` trong chuỗi JSON Request Body.
  - [x] Tạo `IpUtils`: Trích xuất Client IP thực tế ưu tiên từ các header `X-Forwarded-For`, `X-Real-IP`, fallback về `request.getRemoteAddr()`.

- [x] **Task 5: Xây dựng Cơ Chế Ghi Log Bất Đồng Bộ (Spring AOP & Async Events)**
  - [x] Tạo custom annotation `@Auditable(action = ..., module = ..., description = ...)` tại `system/audit/annotation/Auditable.java`.
  - [x] Tạo DTO sự kiện `AuditLogEvent`.
  - [x] Tạo `AuditLogAspect` chặn thực thi phương thức có gắn `@Auditable`:
    - Đo đạc thời gian thực thi (execution time ms).
    - Trích xuất Client IP, Endpoint, HTTP Method, Username từ SecurityContext.
    - Làm sạch Payload qua `DataMaskingUtils`.
    - Phát `AuditLogEvent` qua Spring `ApplicationEventPublisher`.
  - [x] Tạo `AuditLogEventListener` với `@Async` và `@Transactional(propagation = Propagation.REQUIRES_NEW)` để lưu bản ghi vào DB độc lập, không ảnh hưởng tới transaction nghiệp vụ chính.

- [x] **Task 6: Xây dựng Service Layer (`AuditLogService` & `AuditLogServiceImpl`)**
  - [x] Định nghĩa interface `AuditLogService`.
  - [x] Cài đặt `AuditLogServiceImpl`:
    - `getAuditLogs(AuditLogFilterRequest, Pageable)`: Truy vấn danh sách phân trang qua Specification.
    - `getAuditLogDetail(Long id)`: Truy vấn chi tiết, ném `ResourceNotFoundException` nếu không tìm thấy.
    - `getAuditStatistics()`: Tính toán chỉ số thống kê trong ngày.
    - `exportAuditLogsCsv(AuditLogFilterRequest)`: Xuất dữ liệu CSV chuẩn UTF-8 BOM.
    - `saveAuditLog(AuditLogEvent)`: Lưu bản ghi nhật ký.

- [x] **Task 7: Xây dựng REST Controller (`AuditLogController`)**
  - [x] Tạo `AuditLogController` tại đường dẫn `/api/system/audit-logs` có `@PreAuthorize("hasRole('ADMIN')")`.
  - [x] Endpoint `GET /api/system/audit-logs`: Trả về `ApiResponse<PageResponse<AuditLogResponse>>`.
  - [x] Endpoint `GET /api/system/audit-logs/{id}`: Trả về `ApiResponse<AuditLogDetailResponse>`.
  - [x] Endpoint `GET /api/system/audit-logs/statistics`: Trả về `ApiResponse<AuditLogStatsResponse>`.
  - [x] Endpoint `GET /api/system/audit-logs/export`: Trả về `Resource` file CSV kèm header `Content-Disposition: attachment`.

- [x] **Task 8: Gắn `@Auditable` vào các Endpoint Nghiệp Vụ Trọng Yếu**
  - [x] Gắn vào `AuthController`: Đăng nhập, Đăng xuất.
  - [x] Gắn vào `InternController`: Thêm mới, Sửa hồ sơ thực tập sinh.
  - [x] Gắn vào `DocumentController`: Upload, Duyệt tài liệu/CV.
  - [x] Gắn vào `SystemBackupController`: Kích hoạt, Xóa, Tải bản sao lưu.
  - [x] Gắn vào `SystemUserController`: Khóa/Mở khóa tài khoản.

---

### 🔵 GIAI ĐOẠN 2: TÍCH HỢP FRONTEND THỜI GIAN THỰC & LOẠI BỎ MOCK API (`internhub-frontend`)

- [x] **Task 9: Cập nhật TypeScript Types & Interfaces**
  - [x] Bổ sung các type trong `src/types/index.ts`: `AuditModule`, `AuditAction`, `AuditStatus`, `AuditLogItem`, `AuditLogDetail`, `AuditLogStats`, `AuditLogFilterParams`.

- [x] **Task 10: Xây dựng `auditLogService.ts` Chuẩn 100% Real API**
  - [x] Tạo `src/services/auditLogService.ts` sử dụng `apiClient` từ `./api`.
  - [x] Cài đặt các hàm: `getAuditLogs()`, `getAuditLogDetail()`, `getAuditStatistics()`, `exportAuditLogsCsv()`.
  - [x] **Tuyệt đối không lưu mảng `mockAuditLogs` và không fallback về dữ liệu giả lập** khi có lỗi. Nếu API lỗi, ném ngoại lệ rõ ràng để component xử lý.

- [x] **Task 11: Rà soát & Chuẩn hóa `userService.ts`**
  - [x] Tinh chỉnh `userService.ts` để kết nối API thật đồng bộ, loại bỏ fallback mock âm thầm gây hiểu nhầm dữ liệu trên trang Admin.

- [x] **Task 12: Nâng cấp Giao Diện `AdminDashboard.tsx`**
  - [x] Thêm Tabs điều hướng trên Admin Dashboard:
    - Tab 1: Quản lý Tài khoản & Phân quyền
    - Tab 2: Sao lưu Dữ liệu Hệ thống (TM-8)
    - Tab 3: **Nhật ký Hoạt động Hệ thống (TM-9)**
  - [x] Thiết kế bảng danh sách Nhật ký hoạt động chuyên nghiệp:
    - Hiển thị thời gian (định dạng tiếng Việt GMT+7), Người dùng, Vai trò, Phân hệ (Badge màu), Hành động, IP, Trạng thái (`SUCCESS` / `FAILED`).
  - [x] Tích hợp Bộ lọc Đa năng:
    - Ô tìm kiếm từ khóa tức thì (Debounce 400ms).
    - Dropdown chọn Phân hệ (`Tất cả`, `AUTH`, `INTERN`, `DOCUMENT`, `SYSTEM`, `USER`).
    - Dropdown chọn Trạng thái (`Tất cả`, `SUCCESS`, `FAILED`).
    - Bộ chọn ngày: `Từ ngày` - `Đến ngày`.
  - [x] Tích hợp Thẻ KPI Thống kê (Tổng sự kiện hôm nay, Tỷ lệ thành công, v.v.).
  - [x] Nút `Xuất CSV`: Gọi API export và tải file `.csv` về máy tính.
  - [x] Modal xem chi tiết sự kiện: Trình bày chi tiết JSON payload đã mask, Client IP, Trình duyệt / User Agent.
  - [x] Xử lý trải nghiệm người dùng:
    - Hiển thị Skeleton/Spinner khi đang tải dữ liệu (`loading`).
    - Hiển thị thông báo khi danh sách rỗng (`empty state`).
    - Bắt lỗi mạng/API hiển thị Banner cảnh báo rõ ràng kèm nút "Thử lại" (Retry).

---

### 🧪 GIAI ĐOẠN 3: KIỂM THỬ, XÁC MINH & ĐỐI CHIẾU TIÊU CHÍ CHẤP NHẬN

- [x] **Task 13: Viết Unit Tests & WebMvc Tests**
  - [x] Backend Unit Tests: `DataMaskingUtilsTest`, `AuditLogServiceTest`.
  - [x] Backend WebMvc Integration Tests: `AuditLogControllerTest` (kiểm tra phân quyền Admin-only, phân trang, lọc).
  - [x] Đảm bảo 100% tests vượt qua kiểm tra tự động.

- [x] **Task 14: Xác minh Build & Kiểm Tra Hệ Thống**
  - [x] Chạy kiểm tra biên dịch Backend: `cd employee-service; .\gradlew compileJava; cd ..` (BUILD SUCCESSFUL)
  - [x] Chạy Unit Test Backend: `cd employee-service; .\gradlew test; cd ..` (BUILD SUCCESSFUL)
  - [x] Chạy đóng gói JAR: `cd employee-service; .\gradlew bootJar; cd ..` (BUILD SUCCESSFUL)
  - [x] Kiểm tra biên dịch Frontend: `cd internhub-frontend; npm run build; cd ..` (BUILD SUCCESSFUL)
  - [x] Đối chiếu bằng chứng từng Tiêu chí chấp nhận (**AC-1** đến **AC-10**).
