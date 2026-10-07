# Implementation Plan: TM-28 Đăng Ký Xin Nghỉ Phép Cho Thực Tập Sinh (Intern Apply For Leave)

> **Mã Jira Ticket:** [TM-28](https://robluccibn9935.atlassian.net/browse/TM-28)  
> **Tài liệu đặc tả tham chiếu:** [TM-28-apply-for-leave-spec.md](file:///d:/Module_6/InternHub/docs/specs/TM-28-apply-for-leave-spec.md)  
> **Target Microservice:** `intern-and-program-service` (Port: 8082), `api-gateway` (Port: 8080)  
> **Trạng thái:** IMPLEMENTED & VERIFIED  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/Module_6/InternHub/.agents/) và [AGENTS.md](file:///d:/Module_6/InternHub/AGENTS.md) (Rule 1, 2, 14, 15, 16, 17, 18, 19, 21, 22, 23, 27, 30, 31).

---

## 1. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng (Rule 31)

Thực hiện rà soát toàn diện mã nguồn hiện có trong dự án trước khi triển khai:

| Thành phần hiện có | Vị trí trong mã nguồn | Hiện trạng & Khả năng tái sử dụng | Quyết định triển khai |
| :--- | :--- | :--- | :--- |
| **`BaseEntity`** | [`common/entity/BaseEntity.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/common/entity/BaseEntity.java) | Đã quản lý sẵn `id`, `createdAt`, `updatedAt`, `@PrePersist`, `@PreUpdate`. | **Tái sử dụng 100%**: `LeaveRequest` bắt buộc kế thừa `BaseEntity` (Rule 15). |
| **`InternProfile`** | [`intern/entity/InternProfile.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternProfile.java) | Quản lý thông tin Intern: `userId`, `internCode`, `fullName`, `status`, `mentorId`. | **Tái sử dụng 100%**: Xác định Intern hiện tại qua `InternProfileRepository.findByUserId(userId)`. |
| **`InternMentorAssignment`** | [`intern/entity/InternMentorAssignment.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternMentorAssignment.java) | Quản lý quan hệ phân công Mentor - Intern trạng thái `ACTIVE`. | **Tái sử dụng**: Xác định Mentor phụ trách để nhận thông báo và phân quyền xét duyệt. |
| **`NotificationEventDispatcher`** | [`intern/client/NotificationEventDispatcher.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/client/NotificationEventDispatcher.java) | Đã có sẵn cơ chế dispatch thông báo bất đồng bộ (`@Async`) qua Feign client. | **Tái sử dụng 100%**: Bắn sự kiện thông báo cho Mentor khi có đơn mới và cho Intern khi đơn được duyệt/từ chối. |
| **`ApiResponse<T>` & `PageResponse<T>`** | [`common/dto/response/`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/common/dto/response/) | Format phản hồi chuẩn hóa toàn hệ thống và phân trang 0-indexed Spring Data. | **Tái sử dụng 100%** (Rule 19, 20). |
| **`CustomUserDetails`** | [`security/CustomUserDetails.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/security/CustomUserDetails.java) | Trích xuất an toàn `userId`, `username`, roles từ SecurityContextHolder. | **Tái sử dụng 100%**. |
| **Package Quản Lý Nghỉ Phép** | Chưa tồn tại trong hệ thống | Chưa có cấu trúc lưu trữ và xử lý đơn xin nghỉ phép. | **Tạo mới package-by-feature**: `org.example.internservice.leave` (Rule 14). |

---

## 2. Danh Sách Tệp Tác Động (Impacted Files)

Tất cả các tệp mới thuộc package `org.example.internservice.leave` trong `intern-and-program-service`, trừ tệp cấu hình Gateway:

### A. Entities & Enums
- `[NEW]` `LeaveType.java` (`org.example.internservice.leave.entity.enums.LeaveType`): `SICK`, `PERSONAL`, `ACADEMIC_EXAM`, `BEREAVEMENT`, `OTHER`.
- `[NEW]` `LeaveDurationType.java` (`org.example.internservice.leave.entity.enums.LeaveDurationType`): `FULL_DAY`, `MORNING`, `AFTERNOON`.
- `[NEW]` `LeaveStatus.java` (`org.example.internservice.leave.entity.enums.LeaveStatus`): `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`.
- `[NEW]` `LeaveRequest.java` (`org.example.internservice.leave.entity.LeaveRequest`): Kế thừa `BaseEntity`, liên kết `@ManyToOne` với `InternProfile`, đánh index `(intern_id)`, `(status)`, `(start_date, end_date)`.

### B. Repositories
- `[NEW]` `LeaveRequestRepository.java` (`org.example.internservice.leave.repository.LeaveRequestRepository`): Kế thừa `JpaRepository` & `JpaSpecificationExecutor`, chứa query kiểm tra trùng lặp thời gian nghỉ `existsOverlappingActiveRequest` và query phân trang kèm `JOIN FETCH l.intern`.

### C. DTOs
- `[NEW]` `CreateLeaveRequest.java` (`org.example.internservice.leave.dto.request.CreateLeaveRequest`): DTO tạo đơn kèm validation (`@NotNull`, `@NotBlank`, `@Size`).
- `[NEW]` `ApproveLeaveRequest.java` (`org.example.internservice.leave.dto.request.ApproveLeaveRequest`): Ghi chú duyệt tùy chọn (`approvalNote`).
- `[NEW]` `RejectLeaveRequest.java` (`org.example.internservice.leave.dto.request.RejectLeaveRequest`): Lý do từ chối bắt buộc (`@NotBlank`, `@Size(min = 5, max = 500)`).
- `[NEW]` `LeaveRequestResponse.java` (`org.example.internservice.leave.dto.response.LeaveRequestResponse`): Chi tiết đơn nghỉ phép trả về Client.
- `[NEW]` `LeaveRequestSummaryResponse.java` (`org.example.internservice.leave.dto.response.LeaveRequestSummaryResponse`): Tóm tắt phục vụ danh sách hiển thị.

### D. Utilities & Services
- `[NEW]` `WorkdayCalculator.java` (`org.example.internservice.leave.util.WorkdayCalculator`): Tính toán số ngày làm việc thực tế (loại bỏ Thứ 7, Chủ Nhật; xử lý nửa ngày `0.5`).
- `[NEW]` `LeaveRequestService.java` (`org.example.internservice.leave.service.LeaveRequestService`): Giao diện nghiệp vụ.
- `[NEW]` `LeaveRequestServiceImpl.java` (`org.example.internservice.leave.service.impl.LeaveRequestServiceImpl`): Cài đặt logic nghiệp vụ, `@Transactional(readOnly = true)`, kiểm tra IDOR, State Machine, dispatch notification.

### E. Controllers
- `[NEW]` `LeaveRequestController.java` (`org.example.internservice.leave.controller.LeaveRequestController`): Cung cấp các REST endpoints `/api/v1/leave-requests/**` với `@PreAuthorize`.

### F. Gateway Configuration
- `[MODIFY]` `api-gateway.yml` ([`config-repo-local/api-gateway.yml`](file:///d:/Module_6/InternHub/config-repo-local/api-gateway.yml)): Khai báo route `leave-request-service` định tuyến `/api/v1/leave-requests/**` về `lb://intern-and-program-service`.

### G. Tests
- `[NEW]` `WorkdayCalculatorTest.java` (`org.example.internservice.leave.util.WorkdayCalculatorTest`)
- `[NEW]` `LeaveRequestServiceImplTest.java` (`org.example.internservice.leave.service.impl.LeaveRequestServiceImplTest`)
- `[NEW]` `LeaveRequestControllerTest.java` (`org.example.internservice.leave.controller.LeaveRequestControllerTest`)

---

## 3. Kế Hoạch Triển Khai Từng Bước (Phased Implementation Steps)

```
[Phase 1: Enums, Entity, DTOs & Repository]
                  ↓
[Phase 2: WorkdayCalculator & Service Business Logic]
                  ↓
[Phase 3: REST Controller & RBAC Permissions]
                  ↓
[Phase 4: API Gateway Route Configuration]
                  ↓
[Phase 5: Unit Tests, Integration Tests & Compilation Verification]
```

### Phase 1: Enums, Entity, DTOs & Repository
1. Tạo 3 Enums nghiệp vụ: `LeaveType`, `LeaveDurationType`, `LeaveStatus`.
2. Tạo Entity `LeaveRequest` kế thừa `BaseEntity`:
   - `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "intern_id")`
   - Đầy đủ các trường thời gian, lý do, trạng thái, người duyệt, thời điểm duyệt/hủy.
   - Các index: `idx_leave_request_intern`, `idx_leave_request_status`, `idx_leave_request_dates`.
3. Tạo các Request và Response DTOs với Bean Validation (`@NotNull`, `@NotBlank`, `@Size`).
4. Tạo `LeaveRequestRepository`:
   - Query kiểm tra trùng lặp `existsOverlappingActiveRequest(internId, startDate, endDate)`.
   - Query phân trang `findByInternIdOrderByCreatedAtDesc`.
   - Query danh sách chờ duyệt theo danh sách Intern của Mentor hoặc toàn hệ thống.

### Phase 2: WorkdayCalculator & Service Business Logic
1. Viết `WorkdayCalculator`:
   - Tính toán số ngày làm việc thực tế giữa 2 mốc `startDate` và `endDate` (bỏ qua `SATURDAY` và `SUNDAY`).
   - Xử lý nửa ngày `MORNING` / `AFTERNOON` gán `0.5` ngày.
2. Triển khai `LeaveRequestServiceImpl`:
   - `createLeaveRequest(userId, request)`:
     - Lấy `InternProfile` từ `userId`. Kiểm tra trạng thái hoạt động của Intern (`IN_PROGRESS` / `APPROVED`).
     - Validate ngày tháng: `startDate <= endDate`. Kiểm tra hạn nộp trước (Lead time).
     - Kiểm tra không cho phép khoảng ngày chỉ toàn cuối tuần (`totalDays > 0`).
     - Kiểm tra trùng lặp thời gian qua `existsOverlappingActiveRequest` $\rightarrow$ Ném `DuplicateResourceException` (HTTP 409) nếu trùng.
     - Lưu bản ghi với trạng thái `PENDING`.
     - Kích hoạt sự kiện gửi thông báo đến Mentor qua `NotificationEventDispatcher`.
   - `cancelLeaveRequest(userId, leaveRequestId)`:
     - Kiểm tra quyền sở hữu IDOR: Đơn phải thuộc về `userId` đang đăng nhập.
     - Kiểm tra trạng thái: Chỉ cho phép hủy khi `status == PENDING` $\rightarrow$ Cập nhật `CANCELLED` và `cancelledAt`.
   - `getMyLeaveRequests(userId, status, year, pageable)`: Tra cứu lịch sử cá nhân phân trang 0-indexed.
   - `getLeaveRequestDetail(userId, leaveRequestId)`: Xem chi tiết đơn (kiểm tra quyền xem: chủ đơn, mentor phụ trách, HR/Admin).
   - `getPendingRequests(userDetails, pageable)`: Lấy danh sách chờ duyệt theo quyền của User (Mentor chỉ thấy interns của mình; HR/Admin thấy tất cả).
   - `approveLeaveRequest(approverUserId, leaveRequestId, request)`:
     - Kiểm tra trạng thái `PENDING`.
     - Kiểm tra thẩm quyền phê duyệt của Mentor đối với Intern.
     - Cập nhật `APPROVED`, `approverId`, `approverName`, `approvedAt`, `approvalNote`.
     - Bắn thông báo kết quả chấp thuận đến Intern.
   - `rejectLeaveRequest(approverUserId, leaveRequestId, request)`:
     - Kiểm tra trạng thái `PENDING`.
     - Cập nhật `REJECTED`, `approverId`, `approverName`, `rejectionReason`.
     - Bắn thông báo kết quả từ chối đến Intern kèm lý do.

### Phase 3: REST Controller & RBAC Permissions
1. Xây dựng `LeaveRequestController` với prefix `/api/v1/leave-requests`:
   - `POST /api/v1/leave-requests`: `@PreAuthorize("hasRole('INTERN')")`
   - `GET /api/v1/leave-requests/my-requests`: `@PreAuthorize("hasRole('INTERN')")`
   - `PATCH /api/v1/leave-requests/{id}/cancel`: `@PreAuthorize("hasRole('INTERN')")`
   - `GET /api/v1/leave-requests/{id}`: `@PreAuthorize("hasAnyRole('INTERN', 'MENTOR', 'HR', 'ADMIN')")`
   - `GET /api/v1/leave-requests/pending`: `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`
   - `PATCH /api/v1/leave-requests/{id}/approve`: `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`
   - `PATCH /api/v1/leave-requests/{id}/reject`: `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`
2. Đóng gói 100% kết quả trong `ResponseEntity<ApiResponse<T>>`.

### Phase 4: API Gateway Route Configuration
1. Mở [`config-repo-local/api-gateway.yml`](file:///d:/Module_6/InternHub/config-repo-local/api-gateway.yml).
2. Thêm route định tuyến:
   ```yaml
   - id: leave-request-service
     uri: lb://intern-and-program-service
     predicates:
       - Path=/api/v1/leave-requests, /api/v1/leave-requests/**
   ```

### Phase 5: Unit Tests, Integration Tests & Compilation Verification
1. Viết Unit Test cho `WorkdayCalculatorTest`: Kiểm tra ngày trong tuần, cuối tuần, vắt tuần, nửa ngày.
2. Viết Unit Test cho `LeaveRequestServiceImplTest`: Nộp đơn hợp lệ, trùng lặp ngày nghỉ, hủy đơn, chặn IDOR, duyệt đơn, từ chối đơn.
3. Viết Controller Test cho `LeaveRequestControllerTest` bằng `@WebMvcTest`.
4. Chạy lệnh Gradle kiểm tra biên dịch và chạy test thành công 100%.

---

## 4. Kế Hoạch Xác Minh & Kiểm Thử (Verification Plan)

### Automated Tests
1. **Kiểm tra biên dịch service:**
   ```powershell
   .\gradlew :intern-and-program-service:compileJava
   ```
2. **Chạy toàn bộ Unit Tests của package leave:**
   ```powershell
   .\gradlew :intern-and-program-service:test --tests "org.example.internservice.leave.*"
   ```

### Manual Verification Scenarios
1. **Test Case 1 (Tạo đơn thành công):** Đăng nhập quyền `INTERN`, gửi đơn nghỉ 2 ngày làm việc trong tuần. Kiểm tra response trả về HTTP 201 kèm status `PENDING`.
2. **Test Case 2 (Chặn trùng ngày nghỉ):** Gửi tiếp đơn thứ hai trùng một trong các ngày trên $\rightarrow$ Kiểm tra nhận về HTTP 409 Conflict.
3. **Test Case 3 (Hủy đơn):** Gọi API hủy đơn khi đang `PENDING` $\rightarrow$ Kiểm tra status chuyển sang `CANCELLED`.
4. **Test Case 4 (Mentor phê duyệt/từ chối):** Đăng nhập tài khoản Mentor phụ trách, duyệt đơn $\rightarrow$ Kiểm tra status chuyển `APPROVED`.
