# Implementation Plan: TM-26 Báo Cáo Đi Làm & Nghỉ Phép Quản Lý Sự Chuyên Cần Của Thực Tập Sinh Cho Vai Trò HR (HR Work Attendance & Leave Report)

> **Mã Jira Ticket:** [TM-26](https://robluccibn9935.atlassian.net/browse/TM-26)  
> **Tài liệu đặc tả tham chiếu:** [TM-26-work-attendance-and-leave-report-spec.md](file:///d:/Module_6/InternHub/docs/specs/TM-26-work-attendance-and-leave-report-spec.md)  
> **Target Microservice:** `intern-and-program-service` (Port: 8082), `api-gateway` (Port: 8080)  
> **Nhánh Git hiện tại:** `feature/TM-26/work-attendance-and-leave-report`  
> **Trạng thái:** DRAFT - Chờ phê duyệt (Pending Approval)  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/Module_6/InternHub/.agents/) và [AGENTS.md](file:///d:/Module_6/InternHub/AGENTS.md) (Rule 1, 2, 14, 16, 17, 18, 19, 20, 21, 22, 23, 27, 30, 31).

---

## 1. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng (Rule 31 - Reuse First)

Thực hiện rà soát toàn diện mã nguồn hiện có trên nhánh `feature/TM-26/work-attendance-and-leave-report` trước khi bắt tay vào triển khai:

| Thành phần hiện có | Vị trí trong mã nguồn | Hiện trạng & Khả năng tái sử dụng | Quyết định triển khai |
| :--- | :--- | :--- | :--- |
| **`Attendance` Entity** | [`attendance/entity/Attendance.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/entity/Attendance.java) | Đã lưu `internId`, `workDate`, `checkInTime`, `checkOutTime`, `totalWorkingHours`, `status` (`ON_TIME`, `LATE`, `EARLY_LEAVE`, `LATE_AND_EARLY_LEAVE`, `ABSENT`). | **Tái sử dụng 100%**: Không thêm cột mới vào bảng `attendances`. |
| **`LeaveRequest` Entity** | [`leave/entity/LeaveRequest.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/leave/entity/LeaveRequest.java) | Đã lưu `intern`, `leaveType`, `durationType`, `startDate`, `endDate`, `totalDays`, `status` (`APPROVED`). | **Tái sử dụng 100%**: Truy vấn các đơn `status = 'APPROVED'` trong khoảng ngày báo cáo để tính số ngày nghỉ có phép và lý do nghỉ. |
| **`InternProfile` Entity** | [`intern/entity/InternProfile.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternProfile.java) | Quản lý thông tin Intern: `internCode`, `fullName`, `email`, `program` (`InternshipProgram`), `desiredDepartmentId`, `desiredDepartmentName`, `mentorId`, `mentorName`, `startDate`, `endDate`, `status`. | **Tái sử dụng 100%**: Sử dụng để lọc theo phòng ban, chương trình, mentor và tính ngày công thực tế của từng TTS. |
| **`Department` & `InternshipProgram`** | [`program/entity/Department.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/entity/Department.java), [`program/entity/InternshipProgram.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/entity/InternshipProgram.java) | Quản lý thông tin Phòng ban và Chương trình đào tạo. | **Tái sử dụng 100%**: Hỗ trợ bộ lọc và hiển thị tên phòng ban qua `intern.getProgram().getDepartment().getName()` (fallback `desiredDepartmentName`). |
| **`ApiResponse<T>` & `PageResponse<T>`** | [`common/dto/response/`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/common/dto/response/) | Format phản hồi chuẩn hóa toàn hệ thống (`code`, `message`, `data`, `timestamp`) và phân trang 0-indexed (`items`, `currentPage`, `totalItems`...). | **Tái sử dụng 100%** (Rule 19, 20). |
| **`CustomUserDetails`** | [`security/CustomUserDetails.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/security/CustomUserDetails.java) | Trích xuất an toàn `userId`, `username`, roles từ Spring Security Context. | **Tái sử dụng 100%**. |
| **Bảng CSDL MySQL (`internhub_db`)** | CSDL chung | Đã đầy đủ bảng: `attendances`, `leave_requests`, `intern_profiles`, `departments`, `internship_programs`. | **KHÔNG TẠO BẢNG MỚI**: 0 migration script, 0 DDL thay đổi. Toàn bộ báo cáo là dynamic aggregation trong bộ nhớ. |

---

## 2. Danh Sách Tệp Tác Động (Impacted Files)

Tất cả các tệp mới thuộc package chuyên trách `org.example.internservice.attendance.report` trong `intern-and-program-service`:

### A. Enums Nghiệp Vụ Mới
- `[NEW]` `DiligenceStatus.java` (`org.example.internservice.attendance.report.enums.DiligenceStatus`): `EXCELLENT`, `GOOD`, `WARNING`, `CRITICAL`.
- `[NEW]` `DailyDayStatus.java` (`org.example.internservice.attendance.report.enums.DailyDayStatus`): `PRESENT_ON_TIME`, `PRESENT_LATE`, `PRESENT_EARLY_LEAVE`, `PRESENT_LATE_AND_EARLY_LEAVE`, `LEAVE_APPROVED`, `LEAVE_PENDING`, `ABSENT_UNEXCUSED`, `WEEKEND`, `NOT_ENROLLED`, `FUTURE_DAY`.

### B. Request & Response DTOs
- `[NEW]` `AttendanceReportFilterRequest.java` (`...dto.request`): Tham số lọc báo cáo (`month`, `year`, `startDate`, `endDate`, `departmentId`, `programId`, `mentorId`, `diligenceStatus`, `keyword`).
- `[NEW]` `AttendanceSummaryKpiResponse.java` (`...dto.response`): DTO chứa Dashboard KPI tổng quan.
- `[NEW]` `InternDiligenceReportResponse.java` (`...dto.response`): DTO chứa bản ghi báo cáo của từng Intern trong danh sách phân trang.
- `[NEW]` `InternAttendanceDetailReportResponse.java` (`...dto.response`): DTO chứa thông tin chi tiết bảng công của 1 Intern.
- `[NEW]` `DailyAttendanceDetailItem.java` (`...dto.response`): DTO chứa dữ liệu chi tiết của từng ngày trong bảng công.

### C. Repository Updates (Bổ Sung Batch Query Methods)
- `[MODIFY]` [`AttendanceRepository.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/repository/AttendanceRepository.java): Bổ sung method:
  ```java
  List<Attendance> findByInternIdInAndWorkDateBetweenOrderByWorkDateAsc(List<Long> internIds, LocalDate startDate, LocalDate endDate);
  ```
- `[MODIFY]` [`LeaveRequestRepository.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/leave/repository/LeaveRequestRepository.java): Bổ sung query method nạp các đơn nghỉ phép `APPROVED` theo danh sách interns và khoảng ngày:
  ```java
  @Query("SELECT l FROM LeaveRequest l " +
         "JOIN FETCH l.intern i " +
         "WHERE i.id IN :internIds " +
         "AND l.status = org.example.internservice.leave.entity.enums.LeaveStatus.APPROVED " +
         "AND l.startDate <= :endDate AND l.endDate >= :startDate")
  List<LeaveRequest> findApprovedLeavesByInternIdsAndDateRange(
          @Param("internIds") List<Long> internIds,
          @Param("startDate") LocalDate startDate,
          @Param("endDate") LocalDate endDate);
  ```

### D. Utilities & Helpers (Anti-God-Class < 120 Dòng Mỗi File)
- `[NEW]` `WorkDayCalculator.java` (`...util`): Tiện ích tính toán số ngày làm việc tiêu chuẩn (loại trừ Thứ 7, Chủ Nhật, xử lý ngày bắt đầu/kết thúc thực tập thực tế của Intern).
- `[NEW]` `AttendanceCsvExporter.java` (`...util`): Tiện ích xuất dữ liệu báo cáo chuyên cần sang định dạng CSV chuẩn UTF-8 BOM hiển thị chuẩn tiếng Việt trên Excel.

### E. Service & Service Implementation
- `[NEW]` `AttendanceReportService.java` (`...service`): Interface nghiệp vụ định nghĩa 4 chức năng cốt lõi.
- `[NEW]` `AttendanceReportServiceImpl.java` (`...service.impl`): Cài đặt nghiệp vụ tổng hợp, `@Transactional(readOnly = true)`, thuật toán Batch Fetching & In-Memory Aggregation triệt tiêu N+1 Query.

### F. Controller
- `[NEW]` `AttendanceReportController.java` (`...controller`): Cung cấp các REST endpoints `/api/v1/attendances/reports/**` phân quyền RBAC qua `@PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")`.

### G. Unit & Integration Tests
- `[NEW]` `WorkDayCalculatorTest.java` (`...report.util`): Unit tests thuật toán tính ngày công.
- `[NEW]` `AttendanceReportServiceImplTest.java` (`...report.service.impl`): Unit tests logic tổng hợp và tính tỷ lệ chuyên cần.
- `[NEW]` `AttendanceReportControllerTest.java` (`...report.controller`): WebMvcTest kiểm tra bảo mật RBAC và các HTTP responses.

---

## 3. Kế Hoạch Triển Khai Từng Bước (Phased Implementation Steps)

```text
[Phase 1: Enums & DTOs]
          ↓
[Phase 2: Repository Batch Query Updates]
          ↓
[Phase 3: Utilities (WorkDayCalculator & CsvExporter)]
          ↓
[Phase 4: Service Layer & Batch Aggregation Logic]
          ↓
[Phase 5: Controller & Security RBAC Integration]
          ↓
[Phase 6: Unit & Integration Tests + Compile Verification (Rule 27)]
```

### Phase 1: Enums & DTOs
1. Tạo 2 Enums: `DiligenceStatus` và `DailyDayStatus`.
2. Tạo Request DTO: `AttendanceReportFilterRequest` kèm Bean Validation.
3. Tạo 4 Response DTOs: `AttendanceSummaryKpiResponse`, `InternDiligenceReportResponse`, `InternAttendanceDetailReportResponse`, `DailyAttendanceDetailItem`.

### Phase 2: Repository Batch Query Updates
1. Bổ sung `findByInternIdInAndWorkDateBetweenOrderByWorkDateAsc` vào `AttendanceRepository`.
2. Bổ sung `findApprovedLeavesByInternIdsAndDateRange` vào `LeaveRequestRepository`.

### Phase 3: Utilities (WorkDayCalculator & CsvExporter)
1. Cài đặt `WorkDayCalculator`:
   - Hàm `countWorkingDaysBetween(LocalDate start, LocalDate end)`: Đếm các ngày Thứ 2 đến Thứ 6.
   - Hàm `calculateExpectedWorkDays(InternProfile intern, LocalDate reportStart, LocalDate reportEnd)`: Giao thoa khoảng ngày hiệu lực của Intern với kỳ báo cáo (BR-1).
2. Cài đặt `AttendanceCsvExporter`:
   - Ghi ký tự Byte Order Mark UTF-8 (`\uFEFF`) ở đầu stream.
   - Xây dựng nội dung file CSV báo cáo chuyên cần có tiêu đề rõ ràng, định dạng tỷ lệ phần trăm và ghi chú cảnh báo.

### Phase 4: Service Layer & Batch Aggregation Logic
1. Khai báo interface `AttendanceReportService` với 4 phương thức:
   - `getAttendanceSummaryKpi(filterRequest, userDetails)`
   - `getInternDiligenceReports(filterRequest, pageable, userDetails)`
   - `getInternAttendanceDetailReport(internId, month, year, userDetails)`
   - `exportAttendanceReportCsv(filterRequest, userDetails)`
2. Triển khai `AttendanceReportServiceImpl` (`@Transactional(readOnly = true)`):
   - **Xác định phạm vi dữ liệu (Scope Isolation):**
     + Nếu `ROLE_MENTOR`: Giới hạn truy vấn chỉ lấy TTS của Mentor (`findByMentorId(mentorUserId)`).
     + Nếu `ROLE_HR` hoặc `ROLE_ADMIN`: Cho phép xem toàn bộ hoặc lọc theo `departmentId`, `programId`.
   - **Triệt tiêu N+1 Query:**
     + Truy vấn danh sách `InternProfile` của trang kèm `LEFT JOIN FETCH i.program p LEFT JOIN FETCH p.department`.
     + Lấy `List<Long> internIds`.
     + Gọi 1 câu query nạp toàn bộ `Attendance` trong khoảng thời gian của các `internIds`.
     + Gọi 1 câu query nạp toàn bộ `LeaveRequest` trạng thái `APPROVED` trong khoảng thời gian của các `internIds`.
     + Nhóm vào `Map<Long, List<Attendance>>` và `Map<Long, List<LeaveRequest>>`.
   - **Tính toán chỉ số chuyên cần & Xếp loại:**
     + Áp dụng công thức BR-3 tính `diligenceRate`.
     + Áp dụng bảng tiêu chí BR-4 phân loại `DiligenceStatus` (`EXCELLENT`, `GOOD`, `WARNING`, `CRITICAL`) và sinh chuỗi `warningNotes`.

### Phase 5: Controller & Security RBAC Integration
1. Xây dựng `AttendanceReportController` với tiền tố `@RequestMapping("/api/v1/attendances/reports")`:
   - Endpoint `GET /summary`: `@PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")`
   - Endpoint `GET /interns`: `@PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")`
   - Endpoint `GET /interns/{internId}/details`: `@PreAuthorize("hasAnyRole('HR', 'ADMIN', 'MENTOR')")`
   - Endpoint `GET /export`: `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
2. Đóng gói kết quả bằng `ApiResponse.success(200, message, data)` và `PageResponse.from(page, mapper)`.

### Phase 6: Unit & Integration Tests + Compile Verification (Rule 27)
1. Viết `WorkDayCalculatorTest` (kiểm tra tính ngày công trừ T7/CN, xử lý onboard giữa tháng).
2. Viết `AttendanceReportServiceImplTest` (kiểm tra thuật toán tính toán, phân loại xếp loại kỷ luật).
3. Viết `AttendanceReportControllerTest` (WebMvcTest kiểm tra phân quyền RBAC, HTTP 200 cho HR, HTTP 403 cho Intern).
4. Chạy kiểm tra biên dịch bắt buộc:
   - `.\gradlew :intern-and-program-service:compileJava`
   - `.\gradlew :intern-and-program-service:test`

---

## 4. Chiến Lược Phòng Chống Lỗ Hổng & Ràng Buộc Kỹ Thuật

- **Tuân thủ Anti-God-Class (Rule 17):**
  - Không gộp logic tính ngày vào Service $\rightarrow$ đã tách sang `WorkDayCalculator` (< 90 dòng).
  - Không gộp logic ghi file CSV vào Service $\rightarrow$ đã tách sang `AttendanceCsvExporter` (< 120 dòng).
  - Controller chỉ nhận request và validate $\rightarrow$ duy trì dưới 120 dòng.
  - `AttendanceReportServiceImpl` tập trung vào nghiệp vụ tổng hợp $\rightarrow$ duy trì dưới 280 dòng.
- **Phòng chống tràn bộ nhớ (Anti-OOM):**
  - Giới hạn phân trang tối đa `size <= 100`.
  - Luồng xuất CSV được ghi từng dòng qua `BufferedWriter` vào `ByteArrayOutputStream` thay vì tạo chuỗi khổng lồ trong heap.
- **Bảo mật phân quyền & IDOR:**
  - Token Intern gọi vào báo cáo $\rightarrow$ chặn lập tức tại `@PreAuthorize` (403 Forbidden).
  - Mentor cố tình truyền `departmentId` của phòng ban khác $\rightarrow$ Service ghi đè phạm vi chỉ lọc các Intern do Mentor đó phụ trách.

---

## 5. Tiêu Chí Nghiệm Thu (Acceptance Verification)

| Mã tiêu chí | Điều kiện kiểm thử | Kết quả kỳ vọng |
| :--- | :--- | :--- |
| **AC-1** | HR gọi `GET /api/v1/attendances/reports/summary` | Trả về HTTP 200, `code: 200`, KPI tổng hợp đầy đủ quân số, tỷ lệ chuyên cần, lượt muộn, ngày nghỉ phép. |
| **AC-2** | HR gọi `GET /api/v1/attendances/reports/interns` | Trả về HTTP 200, `PageResponse` chuẩn hóa với `items`, `currentPage`, `totalItems`... |
| **AC-3** | Intern onboard ngày 15/10 trong kỳ tháng 10 | `expectedWorkDays` chỉ tính từ 15/10 đến 31/10, không bị phạt vắng vô cớ cho các ngày 01-14/10. |
| **AC-4** | HR gọi `GET /api/v1/attendances/reports/interns/{id}/details` | Trả về đầy đủ ma trận công từng ngày trong tháng kèm `DailyDayStatus`. |
| **AC-5** | HR gọi `GET /api/v1/attendances/reports/export` | Tải về file `.csv` chuẩn UTF-8 BOM, mở trên Excel không vỡ font tiếng Việt. |
| **AC-6** | Token vai trò `ROLE_INTERN` gọi API báo cáo | Bị chặn ngay lập tức với HTTP 403 Forbidden. |
| **AC-7** | Biên dịch hệ thống | `.\gradlew :intern-and-program-service:compileJava` và `:test` hoàn thành **BUILD SUCCESSFUL**. |
