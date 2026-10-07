# Specification: Chức Năng Đăng Ký Xin Nghỉ Phép Cho Thực Tập Sinh (Intern Apply For Leave)

> **Trạng thái:** IMPLEMENTED & VERIFIED  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-28-apply-for-leave-spec.md`  
> **Dự án:** [InternHub](file:///d:/Module_6/InternHub) (Backend Microservices: `intern-and-program-service`, `api-gateway`)  
> **Mã Jira Ticket:** [TM-28](https://robluccibn9935.atlassian.net/browse/TM-28)  
> **Tiêu đề Jira:** *Intern - Đăng ký xin nghỉ phép (Intern Apply for Leave & Request Management)*  
> **Nhánh Git dự kiến:** `feature/TM-28/apply-for-leave`  
> **Cấp độ thay đổi (Change Level):** **L3** (Tính năng mới, bổ sung thực thể CSDL `leave_requests`, các Enums nghiệp vụ, API nộp đơn / hủy đơn / tra cứu lịch sử nghỉ phép của Thực tập sinh, API duyệt/từ chối cho Mentor & HR, liên kết dữ liệu chuyên cần TM-25 và kích hoạt thông báo thời gian thực qua `NotificationEventDispatcher`).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/Module_6/InternHub/.agents/) và [AGENTS.md](file:///d:/Module_6/InternHub/AGENTS.md) (Toàn bộ 31 nguyên tắc bất biến của Backend, đặc biệt Rule 14, 15, 16, 17, 18, 19, 21, 22, 23, 26, 30, 31).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

> [!IMPORTANT]
> **BẮT BUỘC ĐIỀN ĐẦY ĐỦ**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic, API, validation hay database (từ cấp độ L2 trở lên), **bắt buộc** phải ghi thêm một dòng vào bảng này để giải trình lý do trước khi coi nhiệm vụ là hoàn tất (Tuân thủ Rule 30).

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0.0** | 2026-10-05 | AI Senior Pair-Programmer | `TM-28` | Tạo mới | Thiết kế tài liệu đặc tả kỹ thuật toàn diện 14 phần cho phân hệ Đăng ký xin nghỉ phép của Thực tập sinh (Intern Apply For Leave), bao gồm luồng nộp đơn, tính số ngày nghỉ làm việc trừ cuối tuần, chống trùng lặp thời gian nghỉ, hủy đơn PENDING, quy trình xét duyệt của Mentor/HR, và ma trận thông báo thời gian thực. |
| **v1.1.0** | 2026-10-05 | AI Senior Pair-Programmer | `TM-28` | Triển khai mã nguồn | Hoàn tất cài đặt toàn bộ 5 Phase theo Plan: Enums, Entity, DTOs, Repository, Service, Controller, Gateway Route và Unit Tests đạt 100% Pass. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Đăng ký xin nghỉ phép cho Thực tập sinh (Intern Apply for Leave & Leave Request Management).
- **Jira Ticket:** [TM-28](https://robluccibn9935.atlassian.net/browse/TM-28)
- **Tuyên Ngôn Nghiệp Vụ Cốt Lõi (Core Business Statement):**
  > **"MINH BẠCH HÓA LỊCH TRÌNH NGHỈ PHÉP, ĐẢM BẢO TÍNH KỶ LUẬT ĐÀO TẠO VÀ ĐỒNG BỘ DỮ LIỆU CHUYÊN CẦN"**
  >
  > Trong giai đoạn tham gia chương trình thực tập tại doanh nghiệp:
  > - **Thực tập sinh (`ROLE_INTERN`):** Khi có việc cá nhân, đau ốm, hoặc lịch thi cử/bảo vệ tốt nghiệp tại trường đại học, TTS phải chủ động tạo **Đơn xin nghỉ phép trực tuyến (`LeaveRequest`)** trên hệ thống thay vì nhắn tin riêng lẻ hoặc nghỉ tự do.
  > - **Mentor phụ trách (`ROLE_MENTOR`):** Nhận được thông báo thời gian thực ngay khi TTS thuộc quyền quản lý nộp đơn, có trách nhiệm xem xét khối lượng công việc hiện tại, dự án đang chạy để **Phê duyệt (`APPROVED`)** hoặc **Từ chối (`REJECTED`)** kịp thời.
  > - **Bộ phận Nhân sự (`ROLE_HR` & `ROLE_ADMIN`):** Giám sát tổng thể tỷ lệ nghỉ phép, có thẩm quyền xử lý các trường hợp ngoại lệ hoặc phê duyệt thay thế khi Mentor vắng mặt.
  > - **Hệ thống Chấm công ([TM-25](file:///d:/Module_6/InternHub/docs/specs/TM-25-check-in-check-out-spec.md)):** Tự động đồng bộ các ngày nghỉ đã được phê duyệt (`APPROVED`) để phân loại chính xác ngày vắng mặt có phép trong bảng tổng hợp chuyên cần tháng (`MonthlyAttendanceSummaryResponse`), bảo vệ quyền lợi đánh giá thực tập cuối kỳ.

- **Target Microservices:**
  1. `intern-and-program-service` (Port: 8082):
     - Xây dựng package-by-feature mới: `org.example.internservice.leave` bao gồm `entity`, `repository`, `service`, `controller`, `dto`.
     - Lưu trữ thực thể `LeaveRequest` kế thừa `BaseEntity` trong cơ sở dữ liệu `internhub_db`.
     - Tích hợp `NotificationEventDispatcher` gửi thông báo nội bộ đa phương thức (Web notification / WebSocket) đến Mentor và TTS.
     - Cung cấp API tích hợp hoặc helper method cho phân hệ Chấm công (`attendance`) để truy vấn các ngày nghỉ hợp lệ.
  2. `api-gateway` (Port: 8080):
     - Khai báo định tuyến Gateway route: `/api/v1/leave-requests/**` trỏ về `lb://intern-and-program-service`.

- **Target Users & Roles:**
  - **`ROLE_INTERN` (Người tạo đơn - Primary Actor):** Nộp đơn xin nghỉ phép, theo dõi tiến độ xét duyệt, xem lịch sử các đơn đã nộp, tự hủy đơn khi đơn còn đang chờ duyệt (`PENDING`).
  - **`ROLE_MENTOR` (Người xét duyệt trực tiếp - Approver Actor):** Xem danh sách đơn cần duyệt của các interns do mình phụ trách, xem chi tiết lý do và minh chứng đính kèm, thực hiện Phê duyệt (`APPROVED`) hoặc Từ chối (`REJECTED` kèm lý do).
  - **`ROLE_HR` & `ROLE_ADMIN` (Người giám sát & Quản trị):** Xem toàn bộ đơn nghỉ phép trong toàn bộ công ty/chương trình đào tạo, phê duyệt thẩm quyền cao nhất.

- **Change Level:** **L3** (Tính năng mới, tạo bảng cơ sở dữ liệu `leave_requests`, Enums mới, kiểm soát trạng thái State Machine, xác thực IDOR, tích hợp Notification service).

---

### 1.1. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng (Project Scan & Reuse Evaluation - Tuân Thủ Rule 31)

Thực hiện rà soát toàn diện mã nguồn hiện có trong `intern-and-program-service` trước khi thiết kế:

| Thành phần hiện có | Vị trí trong source code | Hiện trạng & Khả năng tái sử dụng cho TM-28 | Quyết định kỹ thuật |
| :--- | :--- | :--- | :--- |
| **`BaseEntity`** | [`common/entity/BaseEntity.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/common/entity/BaseEntity.java) | Đã có sẵn quản lý `id`, `createdAt`, `updatedAt`, `@PrePersist`, `@PreUpdate`. | **Tái sử dụng 100%**: `LeaveRequest` bắt buộc kế thừa `BaseEntity` (Tuân thủ Rule 15). |
| **`InternProfile`** | [`intern/entity/InternProfile.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternProfile.java) | Đã có sẵn thông tin TTS: `userId`, `internCode`, `fullName`, `status`, `mentorId`, `mentorName`, `mentorEmail`. | **Tái sử dụng 100%**: Truy vấn thông tin Intern từ JWT `userId` qua `InternProfileRepository.findByUserId(userId)` giống cách triển khai chuẩn ở TM-25. |
| **`InternMentorAssignment`** | [`intern/entity/InternMentorAssignment.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternMentorAssignment.java) | Quản lý quan hệ phân công Mentor - Intern đang hoạt động (`ACTIVE`). | **Tái sử dụng**: Sử dụng để xác định chính xác Mentor trực tiếp nhận thông báo và có quyền duyệt đơn. |
| **`Attendance`** | [`attendance/entity/Attendance.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/attendance/entity/Attendance.java) | Bảng chấm công hiện tại lưu `workDate`, `status` (`ON_TIME`, `LATE`, `EARLY_LEAVE`, `ABSENT`). | **Tái sử dụng & Tích hợp**: Tra cứu các ngày đã có đơn nghỉ phép `APPROVED` để giải trình tính hợp lệ, không đánh vắng `ABSENT`. |
| **`NotificationEventDispatcher`** | [`intern/client/NotificationEventDispatcher.java`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/client/NotificationEventDispatcher.java) | Đã có sẵn cơ chế gửi thông báo phi chặn (`@Async`) qua Feign client đến `notification-service`. | **Tái sử dụng 100%**: Bắn sự kiện thông báo cho Mentor khi có đơn mới và cho Intern khi đơn được duyệt/từ chối. |
| **`ApiResponse<T>` & `PageResponse<T>`** | [`common/dto/response/`](file:///d:/Module_6/InternHub/intern-and-program-service/src/main/java/org/example/internservice/common/dto/response/) | Format phản hồi chuẩn hóa toàn hệ thống và phân trang 0-indexed Spring Data. | **Tái sử dụng 100%** (Tuân thủ Rule 19 & 20). |
| **Bảng / Thực thể Nghỉ phép** | Chưa tồn tại trong hệ thống | Chưa có bảng lưu trữ đơn xin nghỉ phép, các loại nghỉ phép và trạng thái xét duyệt. | **Tạo mới có kiểm soát**: Tạo thực thể `LeaveRequest` và bảng `leave_requests` trong schema `internhub_db`. |

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Chuẩn hóa quy trình xin nghỉ phép (Standardized Leave Workflow):** Chấm dứt tình trạng xin nghỉ qua tin nhắn chat tự phát (Zalo, Skype, Teams). Mọi yêu cầu nghỉ phép đều được ghi nhận có hệ thống, minh bạch về ngày giờ, lý do và minh chứng.
2. **Kiểm soát tính chuyên cần & tiến độ đào tạo (Attendance & Progress Control):** Giúp Mentor và HR nắm bắt chính xác quân số làm việc thực tế hàng ngày, không làm gián đoạn các kế hoạch giao việc ([TM-19](file:///d:/Module_6/InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md)) hay tiến độ sprint của dự án.
3. **Chống gian lận & chồng lấn thời gian (Overlap & Abuse Prevention):** Thuật toán tự động ngăn chặn việc nộp đơn trùng lặp thời gian, kiểm tra khoảng thời gian hợp lệ, tính chính xác số ngày công thực tế (loại trừ ngày nghỉ cuối tuần).
4. **Phản hồi hai chiều tức thì qua thông báo thời gian thực (Instant Two-Way Feedback):** Mentor nhận thông báo ngay khi TTS nộp đơn; TTS nhận kết quả xét duyệt ngay khi Mentor/HR đưa ra quyết định, nâng cao trải nghiệm ứng dụng.
5. **Cơ sở dữ liệu đánh giá thực tập cuối khóa (Evaluation Data Foundation):** Cung cấp dữ liệu lịch sử nghỉ phép tin cậy cho Module Đánh giá năng lực & thái độ ([TM-23](file:///d:/Module_6/InternHub/docs/specs/TM-23-evaluate-skills-and-attitudes.md)).

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope)

- **Phía Thực tập sinh (`ROLE_INTERN`):**
  - **Tạo đơn xin nghỉ phép (`POST /api/v1/leave-requests`):**
    + Chọn loại nghỉ: `SICK` (Ốm đau), `PERSONAL` (Việc riêng), `ACADEMIC_EXAM` (Lịch thi / Bảo vệ đồ án), `BEREAVEMENT` (Gia đình / Tang chế), `OTHER` (Khác).
    + Chọn hình thức nghỉ (`durationType`): `FULL_DAY` (Cả ngày), `MORNING` (Nửa ngày sáng: 08:00 - 12:00), `AFTERNOON` (Nửa ngày chiều: 13:30 - 17:30).
    + Chọn ngày bắt đầu (`startDate`) và ngày kết thúc (`endDate`). Tự động tính toán tổng số ngày nghỉ (`totalDays`) chỉ tính các ngày làm việc từ Thứ Hai đến Thứ Sáu (bỏ qua Thứ Bảy, Chủ Nhật).
    + Nhập lý do bắt buộc (từ 10 đến 500 ký tự).
    + Đính kèm đường dẫn tài liệu minh chứng (`attachmentUrl`) dạng URL (file đã tải lên MinIO/S3 hoặc Google Drive).
  - **Hủy đơn xin nghỉ phép (`PATCH /api/v1/leave-requests/{id}/cancel`):**
    + Cho phép Intern tự hủy đơn khi đơn vẫn ở trạng thái `PENDING`.
    + Sau khi hủy, trạng thái chuyển sang `CANCELLED` và bắn thông báo thu hồi cho Mentor.
  - **Xem danh sách đơn cá nhân (`GET /api/v1/leave-requests/my-requests`):**
    + Hỗ trợ bộ lọc theo trạng thái (`status`: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`), năm (`year`), phân trang 0-indexed (`page`, `size`, `sort`).
  - **Xem chi tiết đơn (`GET /api/v1/leave-requests/{id}`):**
    + Xem thông tin chi tiết của đơn, lý do, người duyệt, thời gian duyệt, phản hồi từ Mentor.

- **Phía Xét duyệt - Mentor & HR (`ROLE_MENTOR`, `ROLE_HR`, `ROLE_ADMIN`):**
  - **Xem danh sách đơn chờ duyệt (`GET /api/v1/leave-requests/pending`):**
    + Mentor xem danh sách đơn `PENDING` của các TTS do mình phụ trách.
    + HR / Admin xem danh sách đơn `PENDING` của toàn bộ hệ thống.
  - **Phê duyệt đơn (`PATCH /api/v1/leave-requests/{id}/approve`):**
    + Duyệt đơn, cập nhật trạng thái sang `APPROVED`, lưu `approverId`, `approverName`, `approvedAt`, và ghi chú tùy chọn `approvalNote`.
  - **Từ chối đơn (`PATCH /api/v1/leave-requests/{id}/reject`):**
    + Từ chối đơn, bắt buộc nhập lý do từ chối `rejectionReason` (tối thiểu 5 ký tự), cập nhật trạng thái sang `REJECTED`.

- **Tích hợp Thông báo (`NotificationEventDispatcher`):**
  - Gửi thông báo đến Mentor khi có đơn mới (`LEAVE_REQUEST_SUBMITTED`).
  - Gửi thông báo đến Intern khi đơn được duyệt (`LEAVE_REQUEST_APPROVED`) hoặc từ chối (`LEAVE_REQUEST_REJECTED`).
  - Gửi thông báo đến Mentor khi Intern hủy đơn (`LEAVE_REQUEST_CANCELLED`).

- **Định tuyến Gateway:** Cấu hình route `/api/v1/leave-requests/**` trong `api-gateway`.

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn chặn suy diễn sai*)

- **Tuyệt đối KHÔNG triển khai:**
  - Không xây dựng tính năng upload trực tiếp file Multipart vào cùng endpoint nộp đơn (URL minh chứng `attachmentUrl` được lấy sau khi upload qua module storage riêng sẵn có).
  - Không trừ lương hoặc tính công tiền mặt (hệ thống phục vụ mục tiêu quản lý thực tập sinh đào tạo, không phải ERP tính lương doanh nghiệp).
  - Không cho phép chỉnh sửa nội dung đơn sau khi đã nộp (nếu gửi sai, Intern phải hủy đơn `CANCELLED` và tạo đơn mới).
  - Không can thiệp mã nguồn Frontend React/TypeScript (Tuân thủ nghiêm ngặt Rule 7).
  - Không tự tiện commit, push Git hay chạy SQL phá hoại (Tuân thủ Rule 4, 8).

---

## 4. Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Edge Cases)

### 4.1. Edge Case 1: Lỗ hổng Trùng Lặp Thời Gian Nghỉ (Overlapping Leave Requests)
- **Vấn đề:** TTS cố tình hoặc vô ý tạo hai đơn xin nghỉ phép có khoảng ngày chồng lấn nhau (ví dụ: Đơn 1 xin nghỉ từ 10/10 đến 12/10 đang `PENDING` hoặc `APPROVED`, Intern tiếp tục tạo Đơn 2 xin nghỉ từ 11/10 đến 15/10).
- **Hậu quả:** Gây sai lệch thống kê số ngày nghỉ, xung đột trạng thái xét duyệt của Mentor.
- **Giải pháp:**
  - Viết câu query kiểm tra tại Repository:
    ```java
    @Query("SELECT COUNT(l) > 0 FROM LeaveRequest l " +
           "WHERE l.intern.id = :internId " +
           "AND l.status IN ('PENDING', 'APPROVED') " +
           "AND l.startDate <= :endDate " +
           "AND l.endDate >= :startDate")
    boolean existsOverlappingActiveRequest(
            @Param("internId") Long internId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    ```
  - Nếu trả về `true`, ném ngoại lệ `DuplicateResourceException("Bạn đã có đơn xin nghỉ phép khác đang chờ duyệt hoặc đã được phê duyệt trong khoảng thời gian này")` kèm HTTP 409 Conflict.

### 4.2. Edge Case 2: Lỗ hổng IDOR - Truy cập và can thiệp đơn của người khác
- **Vấn đề:** Thực tập sinh A gửi request `PATCH /api/v1/leave-requests/99/cancel` hoặc `GET /api/v1/leave-requests/99` để hủy hoặc xem trộm đơn của Thực tập sinh B.
- **Giải pháp:**
  - Tầng Service trích xuất `userId` từ token xác thực và đối chiếu quyền sở hữu:
    ```java
    if (!leaveRequest.getIntern().getUserId().equals(currentUserId)) {
        throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn xin nghỉ phép của người khác");
    }
    ```
  - Đối với Mentor, kiểm tra Intern làm đơn có thuộc danh sách phân công của Mentor hay không trước khi cho phép duyệt.

### 4.3. Edge Case 3: Chạy đua đồng thời khi duyệt/hủy đơn (Concurrent State Modification)
- **Vấn đề:** Mentor đang bấm nút `Approve`, cùng lúc đó Intern bấm `Cancel` trên trình duyệt. Cả 2 request đến máy chủ cùng mili-giây.
- **Giải pháp:**
  - Sử dụng khóa lạc quan `@Version private Long version;` trên thực thể `LeaveRequest` hoặc câu lệnh UPDATE có điều kiện trạng thái:
    ```sql
    UPDATE leave_requests 
    SET status = 'APPROVED', approver_id = :approverId, updated_at = NOW() 
    WHERE id = :id AND status = 'PENDING';
    ```
  - Nếu số bản ghi cập nhật bằng 0, ném `BadRequestException("Đơn xin nghỉ phép đã được xử lý hoặc không còn ở trạng thái chờ duyệt")` (HTTP 400).

### 4.4. Edge Case 4: Đăng ký nghỉ vào ngày trong quá khứ hoặc ngày cuối tuần thuần túy
- **Vấn đề:** 
  1. Intern đăng ký nghỉ cho ngày đã qua nhiều tuần trước.
  2. Intern chọn khoảng ngày rơi hoàn toàn vào Thứ Bảy và Chủ Nhật (ví dụ: từ Thứ Bảy 10/10 đến Chủ Nhật 11/10).
- **Giải pháp:**
  - Ràng buộc: `startDate` không được trước ngày hiện tại quá 1 ngày (chỉ cho phép tối đa 24h đối với trường hợp nghỉ ốm đột xuất `SICK`, các loại khác bắt buộc `startDate >= today`).
  - Kiểm tra `totalDays`: Tính toán số ngày làm việc thực tế (Thứ 2 - Thứ 6). Nếu trong khoảng từ `startDate` đến `endDate` không có ngày làm việc nào (`totalDays == 0`), ném `BadRequestException("Khoảng thời gian nghỉ chỉ bao gồm ngày nghỉ cuối tuần, không cần tạo đơn xin nghỉ phép")`.

### 4.5. Edge Case 5: Xung đột dữ liệu Nửa ngày (`MORNING` / `AFTERNOON`) và Khoảng ngày
- **Vấn đề:** Intern chọn hình thức nghỉ nửa ngày `MORNING` nhưng lại nhập `startDate = 2026-10-10` và `endDate = 2026-10-15`.
- **Giải pháp:**
  - Kiểm tra tính nhất quán logic ở tầng Service: Nếu `durationType` là `MORNING` hoặc `AFTERNOON`, bắt buộc `startDate.isEqual(endDate)` và gán `totalDays = 0.5`. Nếu vi phạm, ném `BadRequestException("Hình thức nghỉ nửa ngày chỉ áp dụng cho một ngày duy nhất")`.

### 4.6. Edge Case 6: Hồ sơ Thực tập sinh không đủ điều kiện (Terminated / Rejected / Pending)
- **Vấn đề:** Tài khoản người dùng đã bị chấm dứt thực tập (`TERMINATED`) hoặc chưa được duyệt vào chương trình (`PENDING`) vẫn cố gửi API xin nghỉ phép.
- **Giải pháp:**
  - Kiểm tra trạng thái của `InternProfile`: Chỉ cho phép khi `status == InternStatus.IN_PROGRESS` (hoặc `APPROVED`). Nếu không, ném `BadRequestException("Hồ sơ thực tập sinh không ở trạng thái hoạt động hợp lệ để xin nghỉ phép")`.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng)

- **FR-1 (Tạo đơn nghỉ phép):** Hệ thống cung cấp API cho phép Thực tập sinh nộp đơn xin nghỉ phép với đầy đủ thông tin: Loại nghỉ, hình thức nghỉ, ngày bắt đầu, ngày kết thúc, lý do và link tài liệu đính kèm.
- **FR-2 (Tự động tính ngày công):** Hệ thống tự động tính toán tổng số ngày làm việc được nghỉ (`totalDays`), loại trừ Thứ Bảy và Chủ Nhật.
- **FR-3 (Tra cứu lịch sử cá nhân):** Hệ thống cho phép Thực tập sinh tra cứu danh sách đơn đã nộp của mình với bộ lọc trạng thái và phân trang 0-indexed.
- **FR-4 (Hủy đơn PENDING):** Hệ thống cho phép Thực tập sinh hủy đơn của mình khi đơn chưa được xét duyệt.
- **FR-5 (Danh sách chờ duyệt cho Mentor/HR):** Hệ thống cung cấp danh sách các đơn cần duyệt cho Mentor phụ trách và HR.
- **FR-6 (Phê duyệt đơn):** Hệ thống cho phép Mentor/HR phê duyệt đơn kèm ghi chú phản hồi.
- **FR-7 (Từ chối đơn):** Hệ thống cho phép Mentor/HR từ chối đơn và bắt buộc nhập lý do từ chối.
- **FR-8 (Bắn thông báo thời gian thực):** Hệ thống tự động kích hoạt thông báo tương ứng cho Mentor hoặc Intern qua `NotificationEventDispatcher` khi có thay đổi trạng thái đơn.
- **FR-9 (Đồng bộ chuyên cần):** Hệ thống cung cấp truy vấn phục vụ module Chấm công để xác định ngày nghỉ có phép hợp lệ.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Quy tắc tính số ngày làm việc - Workday Calculation):**
  - Nếu `durationType` $\in$ {`MORNING`, `AFTERNOON`}: Bắt buộc `startDate == endDate`, tính `totalDays = 0.5`.
  - Nếu `durationType` == `FULL_DAY`: `totalDays` = Tổng các ngày từ `startDate` đến `endDate` mà `DayOfWeek` $\notin$ {`SATURDAY`, `SUNDAY`}.
- **BR-2 (Quy tắc hạn nộp đơn trước - Advance Notice Rule):**
  - Đối với loại `PERSONAL`, `ACADEMIC_EXAM`, `OTHER`: `startDate` phải lớn hơn hoặc bằng ngày hiện tại (`startDate >= LocalDate.now(Asia/Ho_Chi_Minh)`). Khuyến nghị nộp trước tối thiểu 1 ngày làm việc.
  - Đối với loại `SICK` (Nghỉ ốm) và `BEREAVEMENT` (Gia đình): Cho phép nộp bù muộn nhất trong vòng 24 giờ kể từ ngày bắt đầu nghỉ (`startDate >= LocalDate.now().minusDays(1)`).
- **BR-3 (Quy tắc chuyển trạng thái bất khả đảo ngược - State Transition Immutability):**
  - Chỉ đơn có trạng thái `PENDING` mới được phép chuyển sang `APPROVED`, `REJECTED`, hoặc `CANCELLED`.
  - Không cho phép bất kỳ thao tác thay đổi nào trên đơn đã ở trạng thái kết thúc (`APPROVED`, `REJECTED`, `CANCELLED`).
- **BR-4 (Quy tắc phân quyền duyệt - Approval Authority):**
  - `ROLE_MENTOR` chỉ được phép duyệt đơn của những Intern mà Mentor đó đang trực tiếp phụ trách (`InternMentorAssignment.status == ACTIVE` hoặc `InternProfile.mentorId == currentMentorId`).
  - `ROLE_HR` và `ROLE_ADMIN` có toàn quyền duyệt đơn của bất kỳ Intern nào trong hệ thống.
- **BR-5 (Quy tắc lý do từ chối - Rejection Reason Mandatory):**
  - Khi từ chối đơn (`REJECTED`), bắt buộc phải cung cấp `rejectionReason` với độ dài từ 5 đến 500 ký tự để phản hồi cho Thực tập sinh.

---

## 7. Data Model (Mô Hình Dữ Liệu)

### 7.1. Cấu Trúc Bảng MySQL (`leave_requests`)

```sql
CREATE TABLE `leave_requests` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `intern_id` BIGINT NOT NULL,
  `leave_type` VARCHAR(30) NOT NULL,
  `duration_type` VARCHAR(20) NOT NULL,
  `start_date` DATE NOT NULL,
  `end_date` DATE NOT NULL,
  `total_days` DOUBLE NOT NULL,
  `reason` TEXT NOT NULL,
  `attachment_url` VARCHAR(500) DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `approver_id` BIGINT DEFAULT NULL,
  `approver_name` VARCHAR(100) DEFAULT NULL,
  `approved_at` DATETIME DEFAULT NULL,
  `rejection_reason` TEXT DEFAULT NULL,
  `approval_note` TEXT DEFAULT NULL,
  `cancelled_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_leave_request_intern` FOREIGN KEY (`intern_id`) REFERENCES `intern_profiles` (`id`) ON DELETE CASCADE,
  INDEX `idx_leave_request_intern` (`intern_id`),
  INDEX `idx_leave_request_status` (`status`),
  INDEX `idx_leave_request_dates` (`start_date`, `end_date`),
  INDEX `idx_leave_request_approver` (`approver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 7.2. Các Enums Nghiệp Vụ

1. **`LeaveType` (`org.example.internservice.leave.entity.enums.LeaveType`):**
   - `SICK`: Nghỉ ốm đau / khám chữa bệnh.
   - `PERSONAL`: Nghỉ giải quyết việc riêng cá nhân.
   - `ACADEMIC_EXAM`: Nghỉ thi học kỳ, bảo vệ đồ án tốt nghiệp tại trường.
   - `BEREAVEMENT`: Nghỉ việc hiếu hỉ, tang lễ gia đình.
   - `OTHER`: Lý do chính đáng khác.

2. **`LeaveDurationType` (`org.example.internservice.leave.entity.enums.LeaveDurationType`):**
   - `FULL_DAY`: Nghỉ trọn vẹn cả ngày (hoặc nhiều ngày liên tiếp).
   - `MORNING`: Nghỉ nửa ngày buổi sáng (08:00 - 12:00).
   - `AFTERNOON`: Nghỉ nửa ngày buổi chiều (13:30 - 17:30).

3. **`LeaveStatus` (`org.example.internservice.leave.entity.enums.LeaveStatus`):**
   - `PENDING`: Đang chờ duyệt (Trạng thái khởi tạo).
   - `APPROVED`: Đã được Mentor hoặc HR chấp thuận.
   - `REJECTED`: Bị từ chối.
   - `CANCELLED`: Thực tập sinh chủ động hủy đơn khi còn PENDING.

### 7.3. JPA Entity Mapping (`LeaveRequest.java`)

```java
package org.example.internservice.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.internservice.common.entity.BaseEntity;
import org.example.internservice.intern.entity.InternProfile;
import org.example.internservice.leave.entity.enums.LeaveDurationType;
import org.example.internservice.leave.entity.enums.LeaveStatus;
import org.example.internservice.leave.entity.enums.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests", indexes = {
        @Index(name = "idx_leave_request_intern", columnList = "intern_id"),
        @Index(name = "idx_leave_request_status", columnList = "status"),
        @Index(name = "idx_leave_request_dates", columnList = "start_date, end_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternProfile intern;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 30)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_type", nullable = false, length = 20)
    private LeaveDurationType durationType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false)
    private Double totalDays;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approver_name", length = 100)
    private String approverName;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "approval_note", columnDefinition = "TEXT")
    private String approvalNote;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
```

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

Tất cả các API tuân thủ chuẩn RESTful danh từ số nhiều `/api/v1/leave-requests`, đóng gói `ApiResponse<T>`, xác thực Bearer JWT Header.

### 8.1. API 1: Tạo mới đơn xin nghỉ phép (`POST /api/v1/leave-requests`)

- **Mục đích:** Thực tập sinh nộp đơn xin nghỉ phép mới.
- **Phân quyền:** `@PreAuthorize("hasRole('INTERN')")`
- **Request Headers:**
  ```http
  Authorization: Bearer <jwt-token-of-intern>
  Content-Type: application/json
  ```
- **Request Body JSON (`CreateLeaveRequest.java`):**
  ```json
  {
    "leaveType": "ACADEMIC_EXAM",
    "durationType": "FULL_DAY",
    "startDate": "2026-10-15",
    "endDate": "2026-10-16",
    "reason": "Em xin phép nghỉ để tham gia kỳ thi vấn đáp tốt nghiệp tại trường Đại học Bách Khoa.",
    "attachmentUrl": "https://storage.internhub.io/documents/exam_schedule_2026.pdf"
  }
  ```
- **Response Success (201 Created):**
  ```json
  {
    "code": 201,
    "message": "Nộp đơn xin nghỉ phép thành công. Đơn của bạn đã được gửi tới Mentor phụ trách.",
    "data": {
      "id": 12,
      "internId": 5,
      "internCode": "INT-2026-0005",
      "internName": "Nguyễn Văn An",
      "leaveType": "ACADEMIC_EXAM",
      "leaveTypeDescription": "Nghỉ thi cử / Đồ án",
      "durationType": "FULL_DAY",
      "startDate": "2026-10-15",
      "endDate": "2026-10-16",
      "totalDays": 2.0,
      "reason": "Em xin phép nghỉ để tham gia kỳ thi vấn đáp tốt nghiệp tại trường Đại học Bách Khoa.",
      "attachmentUrl": "https://storage.internhub.io/documents/exam_schedule_2026.pdf",
      "status": "PENDING",
      "mentorId": 3,
      "mentorName": "Trần Thị Mai",
      "createdAt": "2026-10-05T14:00:00"
    },
    "timestamp": "2026-10-05T14:00:00"
  }
  ```
- **Response Errors:**
  - `400 Bad Request`: `startDate` sau `endDate`, hoặc lý do dưới 10 ký tự, hoặc ngày nghỉ rơi vào quá khứ.
  - `409 Conflict`: Trùng lặp khoảng thời gian với đơn khác đang `PENDING` hoặc `APPROVED`.

---

### 8.2. API 2: Lấy danh sách đơn nghỉ phép của bản thân (`GET /api/v1/leave-requests/my-requests`)

- **Mục đích:** Thực tập sinh tra cứu lịch sử xin nghỉ phép của chính mình.
- **Phân quyền:** `@PreAuthorize("hasRole('INTERN')")`
- **Query Parameters:**
  - `status` (tùy chọn): `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED`
  - `year` (tùy chọn): `2026`
  - `page` (mặc định: `0`), `size` (mặc định: `10`), `sort` (mặc định: `createdAt,desc`)
- **Response Success (200 OK):**
  ```json
  {
    "code": 200,
    "message": "Lấy danh sách đơn xin nghỉ phép thành công",
    "data": {
      "items": [
        {
          "id": 12,
          "leaveType": "ACADEMIC_EXAM",
          "leaveTypeDescription": "Nghỉ thi cử / Đồ án",
          "durationType": "FULL_DAY",
          "startDate": "2026-10-15",
          "endDate": "2026-10-16",
          "totalDays": 2.0,
          "reason": "Em xin phép nghỉ để tham gia kỳ thi vấn đáp...",
          "status": "PENDING",
          "approverName": null,
          "approvedAt": null,
          "createdAt": "2026-10-05T14:00:00"
        }
      ],
      "currentPage": 0,
      "pageSize": 10,
      "totalItems": 1,
      "totalPages": 1,
      "isFirst": true,
      "isLast": true,
      "hasNext": false,
      "hasPrevious": false
    },
    "timestamp": "2026-10-05T14:05:00"
  }
  ```

---

### 8.3. API 3: Hủy đơn xin nghỉ phép (`PATCH /api/v1/leave-requests/{id}/cancel`)

- **Mục đích:** Thực tập sinh chủ động hủy đơn khi còn đang `PENDING`.
- **Phân quyền:** `@PreAuthorize("hasRole('INTERN')")`
- **Response Success (200 OK):**
  ```json
  {
    "code": 200,
    "message": "Hủy đơn xin nghỉ phép thành công",
    "data": {
      "id": 12,
      "status": "CANCELLED",
      "cancelledAt": "2026-10-05T14:10:00"
    },
    "timestamp": "2026-10-05T14:10:00"
  }
  ```
- **Response Errors:**
  - `400 Bad Request`: Đơn đã được `APPROVED` hoặc `REJECTED`, không thể hủy.
  - `403 Forbidden`: Cố tình hủy đơn của Thực tập sinh khác (IDOR).
  - `404 Not Found`: Không tìm thấy đơn xin nghỉ phép với ID chỉ định.

---

### 8.4. API 4: Lấy chi tiết đơn xin nghỉ phép (`GET /api/v1/leave-requests/{id}`)

- **Mục đích:** Xem đầy đủ thông tin một đơn nghỉ phép cụ thể.
- **Phân quyền:** `@PreAuthorize("hasAnyRole('INTERN', 'MENTOR', 'HR', 'ADMIN')")`
- **Response Success (200 OK):**
  ```json
  {
    "code": 200,
    "message": "Lấy thông tin chi tiết đơn xin nghỉ phép thành công",
    "data": {
      "id": 12,
      "internId": 5,
      "internCode": "INT-2026-0005",
      "internName": "Nguyễn Văn An",
      "internEmail": "an.nguyen@example.com",
      "internPhone": "0987654321",
      "leaveType": "ACADEMIC_EXAM",
      "leaveTypeDescription": "Nghỉ thi cử / Đồ án",
      "durationType": "FULL_DAY",
      "startDate": "2026-10-15",
      "endDate": "2026-10-16",
      "totalDays": 2.0,
      "reason": "Em xin phép nghỉ để tham gia kỳ thi vấn đáp tốt nghiệp...",
      "attachmentUrl": "https://storage.internhub.io/documents/exam_schedule_2026.pdf",
      "status": "PENDING",
      "approverId": null,
      "approverName": null,
      "approvedAt": null,
      "rejectionReason": null,
      "approvalNote": null,
      "cancelledAt": null,
      "createdAt": "2026-10-05T14:00:00",
      "updatedAt": "2026-10-05T14:00:00"
    },
    "timestamp": "2026-10-05T14:15:00"
  }
  ```

---

### 8.5. API 5: Danh sách đơn chờ duyệt dành cho Mentor / HR (`GET /api/v1/leave-requests/pending`)

- **Mục đích:** Mentor xem danh sách đơn đang chờ duyệt của interns do mình phụ trách; HR/Admin xem toàn bộ hệ thống.
- **Phân quyền:** `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`
- **Query Parameters:** `page`, `size`, `sort`, `search`
- **Response Success (200 OK):** Trả về `PageResponse<LeaveRequestResponse>`.

---

### 8.6. API 6: Phê duyệt đơn xin nghỉ phép (`PATCH /api/v1/leave-requests/{id}/approve`)

- **Mục đích:** Mentor hoặc HR chấp thuận đơn xin nghỉ phép.
- **Phân quyền:** `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`
- **Request Body JSON (`ApproveLeaveRequest.java` - tùy chọn):**
  ```json
  {
    "approvalNote": "Đồng ý cho em nghỉ ôn thi. Chúc em thi tốt!"
  }
  ```
- **Response Success (200 OK):**
  ```json
  {
    "code": 200,
    "message": "Phê duyệt đơn xin nghỉ phép thành công",
    "data": {
      "id": 12,
      "status": "APPROVED",
      "approverName": "Trần Thị Mai",
      "approvedAt": "2026-10-05T15:00:00",
      "approvalNote": "Đồng ý cho em nghỉ ôn thi. Chúc em thi tốt!"
    },
    "timestamp": "2026-10-05T15:00:00"
  }
  ```

---

### 8.7. API 7: Từ chối đơn xin nghỉ phép (`PATCH /api/v1/leave-requests/{id}/reject`)

- **Mục đích:** Mentor hoặc HR từ chối đơn xin nghỉ phép.
- **Phân quyền:** `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`
- **Request Body JSON (`RejectLeaveRequest.java` - bắt buộc):**
  ```json
  {
    "rejectionReason": "Ngày 15/10 nhóm có buổi Demo quan trọng với khách hàng, em vui lòng dời lịch nghỉ sang tuần sau."
  }
  ```
- **Response Success (200 OK):**
  ```json
  {
    "code": 200,
    "message": "Đã từ chối đơn xin nghỉ phép",
    "data": {
      "id": 12,
      "status": "REJECTED",
      "approverName": "Trần Thị Mai",
      "rejectionReason": "Ngày 15/10 nhóm có buổi Demo quan trọng..."
    },
    "timestamp": "2026-10-05T15:05:00"
  }
  ```

---

## 9. Core Flow / Enforcement Flow (Luồng Xử Lý Cốt Lõi)

### 9.1. Sơ Đồ Trạng Thái Đơn Xin Nghỉ Phép (State Machine)

```
        [ Thực tập sinh tạo đơn ]
                    │
                    ▼
               ( PENDING )
              /     │     \
   Intern hủy       │      Mentor/HR duyệt
            /       │       \
           ▼        ▼        ▼
     (CANCELLED) (REJECTED) (APPROVED)
         [Hết]      [Hết]      │
                               ▼
                   [ Đồng bộ Attendance ]
                   (Tính ngày nghỉ có phép)
```

### 9.2. Trình Tự Thực Thi Nộp Đơn (Sequence Flow)

```
Intern (Web/App)           API Gateway            LeaveRequestService        NotificationDispatcher
      │                         │                         │                            │
      │ 1. POST /leave-requests │                         │                            │
      │────────────────────────>│ 2. Route to InternSvc   │                            │
      │                         │────────────────────────>│                            │
      │                         │                         │ 3. Extract userId          │
      │                         │                         │ 4. Validate Intern Status  │
      │                         │                         │ 5. Validate Dates & Workday│
      │                         │                         │ 6. Check Overlap Conflict  │
      │                         │                         │ 7. Save to DB (PENDING)    │
      │                         │                         │ 8. Dispatch Event ────────>│
      │                         │                         │    (Notify Mentor)         │
      │                         │ 9. Return ApiResponse   │                            │
      │<────────────────────────│<────────────────────────│                            │
      │   HTTP 201 Created      │                         │                            │
```

---

## 10. Non-Functional Requirements & Constraints

- **Hiệu năng (Performance):** Thời gian phản hồi API tra cứu và nộp đơn $< 200\text{ms}$ tại percentiles 95th. Các câu truy vấn có đầy đủ Index trên `intern_id`, `status`, `start_date`, `end_date`.
- **Chống lỗi JPA N+1 Query:** Luôn sử dụng `JOIN FETCH l.intern` khi truy vấn danh sách đơn xin nghỉ phép để nạp thông tin hồ sơ Intern trong 1 truy vấn duy nhất.
- **Tiêu chuẩn Anti-God-Class (Rule 17):** Các class `LeaveRequestController` và `LeaveRequestServiceImpl` giữ dưới 250 dòng. Tách riêng logic tính ngày làm việc sang class tiện ích `WorkdayCalculator`.
- **Quản lý Giao Dịch (`@Transactional` - Rule 23):** Cấp class ServiceImpl đánh dấu `@Transactional(readOnly = true)`. Các hàm tạo, duyệt, hủy đánh dấu `@Transactional` tường minh.
- **Constructor Injection (Rule 21):** Sử dụng `@RequiredArgsConstructor`, tuyệt đối cấm dùng `@Autowired` trên field.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [x] **AC-1 (Nộp đơn thành công):** Thực tập sinh có tài khoản hợp lệ nộp đơn với ngày tháng hợp lệ, lý do đầy đủ nhận về HTTP 201 Created và đơn được lưu ở trạng thái `PENDING`.
- [x] **AC-2 (Chặn trùng lặp ngày nghỉ):** Gửi đơn có khoảng ngày trùng với một đơn khác đang `PENDING` hoặc `APPROVED` của cùng một Intern nhận về HTTP 409 Conflict kèm thông báo tiếng Việt.
- [x] **AC-3 (Tính số ngày chính xác):** Nộp đơn nghỉ từ Thứ Sáu đến Thứ Hai tuần tiếp theo (4 ngày theo lịch) được hệ thống tính đúng `totalDays = 2.0` (chỉ tính Thứ Sáu và Thứ Hai, trừ Thứ Bảy và Chủ Nhật).
- [x] **AC-4 (Hủy đơn thành công):** Thực tập sinh có thể hủy đơn `PENDING` của mình; đơn chuyển sang `CANCELLED`. Thử hủy lại lần nữa hoặc hủy đơn đã `APPROVED` nhận về HTTP 400.
- [x] **AC-5 (Bảo vệ IDOR):** Thực tập sinh A không thể xem hoặc hủy đơn của Thực tập sinh B; hệ thống ném `AccessDeniedException` (HTTP 403).
- [x] **AC-6 (Mentor/HR phê duyệt):** Mentor phụ trách duyệt đơn hợp lệ; trạng thái chuyển sang `APPROVED`, ghi nhận `approverName` và thời điểm duyệt.
- [x] **AC-7 (Mentor/HR từ chối):** Mentor từ chối đơn kèm lý do nhận về kết quả thành công; nếu không nhập lý do từ chối nhận về HTTP 400.
- [x] **AC-8 (Bắn thông báo):** Mentor nhận thông báo khi có đơn mới; Intern nhận thông báo khi đơn được duyệt hoặc từ chối.

---

## 12. Unit & Integration Test Cases Checklist

### 12.1. Unit Test Cases (`LeaveRequestServiceImplTest`)
- [x] **UT-BE-01:** `createLeaveRequest_Success_WhenValid()`: Nộp đơn hợp lệ, tính đúng `totalDays`, lưu DB thành công và bắn thông báo.
- [x] **UT-BE-02:** `createLeaveRequest_ThrowDuplicate_WhenOverlappingRequestExists()`: Ném `DuplicateResourceException` khi đã có đơn trùng ngày.
- [x] **UT-BE-03:** `createLeaveRequest_ThrowBadRequest_WhenStartDateInPast()`: Ném `BadRequestException` khi ngày bắt đầu trong quá khứ.
- [x] **UT-BE-04:** `calculateWorkdays_OnlyWeekend_ThrowBadRequestException()`: Ném `BadRequestException` khi khoảng ngày chỉ toàn Thứ 7 và CN.
- [x] **UT-BE-05:** `cancelLeaveRequest_Success_WhenPendingAndOwner()`: Hủy đơn PENDING thành công.
- [x] **UT-BE-06:** `cancelLeaveRequest_ThrowIllegalState_WhenAlreadyApproved()`: Không cho phép hủy đơn đã APPROVED.
- [x] **UT-BE-07:** `cancelLeaveRequest_ThrowAccessDenied_WhenNotOwner()`: Chặn IDOR khi user khác cố tình hủy đơn.
- [x] **UT-BE-08:** `approveLeaveRequest_Success_WhenApproverIsMentor()`: Mentor duyệt đơn thành công, cập nhật trạng thái `APPROVED`.
- [x] **UT-BE-09:** `rejectLeaveRequest_Success_WhenReasonProvided()`: Mentor từ chối đơn thành công kèm lý do.
- [x] **UT-BE-10:** `getMyLeaveRequests_Success_ReturnPageResponse()`: Lấy danh sách phân trang cá nhân.

### 12.2. Integration & Controller Test Cases (`LeaveRequestControllerTest`)
- [x] **IT-BE-01:** `createLeaveRequest_Return201Created()`: Test nộp đơn thành công 201 Created.
- [x] **IT-BE-02:** `getMyLeaveRequests_Return200Ok()`: Test xem danh sách cá nhân 200 OK.
- [x] **IT-BE-03:** `cancelLeaveRequest_Return200Ok()`: Test hủy đơn cá nhân 200 OK.
- [x] **IT-BE-04:** `approveLeaveRequest_Return200Ok()`: Test duyệt đơn bởi Mentor 200 OK.
- [x] **IT-BE-05:** `rejectLeaveRequest_Return200Ok()`: Test từ chối đơn bởi Mentor 200 OK.

---

## 13. Implementation Checklist (Danh Sách File Triển Khai)

- [x] **Enums:**
  - `org.example.internservice.leave.entity.enums.LeaveType`
  - `org.example.internservice.leave.entity.enums.LeaveDurationType`
  - `org.example.internservice.leave.entity.enums.LeaveStatus`
- [x] **Entity:**
  - `org.example.internservice.leave.entity.LeaveRequest` (Kế thừa `BaseEntity`)
- [x] **Utility:**
  - `org.example.internservice.leave.util.WorkdayCalculator` (Tính toán số ngày làm việc loại trừ T7 & CN)
- [x] **DTOs Request:**
  - `org.example.internservice.leave.dto.request.CreateLeaveRequest`
  - `org.example.internservice.leave.dto.request.ApproveLeaveRequest`
  - `org.example.internservice.leave.dto.request.RejectLeaveRequest`
- [x] **DTOs Response:**
  - `org.example.internservice.leave.dto.response.LeaveRequestResponse`
  - `org.example.internservice.leave.dto.response.LeaveRequestSummaryResponse`
- [x] **Repository:**
  - `org.example.internservice.leave.repository.LeaveRequestRepository`
- [x] **Service:**
  - `org.example.internservice.leave.service.LeaveRequestService`
  - `org.example.internservice.leave.service.impl.LeaveRequestServiceImpl`
- [x] **Controller:**
  - `org.example.internservice.leave.controller.LeaveRequestController`
- [x] **Gateway Config:**
  - Cập nhật route `leave-request-service` trong `config-repo-local/api-gateway.yml`
- [x] **Tests:**
  - `org.example.internservice.leave.util.WorkdayCalculatorTest`
  - `org.example.internservice.leave.service.impl.LeaveRequestServiceImplTest`
  - `org.example.internservice.leave.controller.LeaveRequestControllerTest`
