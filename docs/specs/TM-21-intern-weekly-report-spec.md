# Specification: Thực Tập Sinh - Nộp Báo Cáo Tuần (TM-21)

> **Trạng thái:** DRAFT / PROPOSED (PENDING APPROVAL)  
> **Lưu trữ tại:** `InternHub/docs/specs/TM-21-intern-weekly-report-spec.md`  
> **Dự án:** [InternHub](file:///d:/codegym_final_project/InternHub) (Backend Microservices: `intern-and-program-service`, `api-gateway`)  
> **Mã Jira Ticket:** [TM-21](https://robluccibn9935.atlassian.net/browse/TM-21)  
> **Tiêu đề Jira:** *Intern - Nộp Báo Cáo Tuần (Intern Weekly Report Submission & Task Synthesis)*  
> **Nối tiếp trực tiếp:** [TM-19 (Mentor giao nhiệm vụ)](file:///d:/codegym_final_project/InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md) & [TM-20 (TTS cập nhật tiến độ Kanban)](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md)  
> **Tiếp nối phía sau:** [TM-22 (Mentor xem báo cáo tuần & Đánh giá phản hồi)](file:///d:/codegym_final_project/InternHub/docs/specs/TM-22-mentor-review-weekly-report-spec.md)  
> **Nhánh Git dự kiến:** `feature/TM-21/intern-weekly-report`  
> **Cấp độ thay đổi (Change Level):** **L3** (Tạo mới thực thể `InternWeeklyReport` & bảng snapshot nhiệm vụ `InternWeeklyReportTask`, cơ chế liên kết dữ liệu tự động với bảng nhiệm vụ Kanban TM-20, kiểm soát sửa đổi an toàn theo trạng thái đánh giá của Mentor, API quản lý báo cáo tuần cá nhân cho `ROLE_INTERN`, kích hoạt thông báo thời gian thực qua `NotificationEventDispatcher`).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc bất biến của Backend, đặc biệt Rule 14, 15, 16, 17, 18, 19, 21, 22, 23, 26, 30, 31).

---

## 0. Nhật Ký Thay Đổi & Giải Trình Kỹ Thuật (Revision History & Change Rationale)

> [!IMPORTANT]
> **BẮT BUỘC ĐIỀN ĐẦY ĐỦ**: Bất kể khi nào Lập trình viên hay AI Agent thay đổi mã nguồn ảnh hưởng đến logic, API, validation hay database (từ cấp độ L2 trở lên), **bắt buộc** phải ghi thêm một dòng vào bảng này để giải trình lý do trước khi coi nhiệm vụ là hoàn tất (Tuân thủ Rule 30).

| Phiên bản | Ngày | Người thực hiện | Task / Jira | Loại thay đổi | Lý do & Giải trình kỹ thuật (Rationale) |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **v1.0.0** | 2026-10-07 | AI Senior Pair-Programmer & User | `TM-21` | Tạo mới | Khởi tạo tài liệu đặc tả kỹ thuật Backend cho TM-21 sau khi Người dùng phê duyệt đề xuất nghiệp vụ [TM-21-business-proposal.md](file:///C:/Users/admin/.gemini/antigravity-ide/brain/6f23f4e4-cd13-4ddc-af3e-62b61766f750/TM-21-business-proposal.md). Chuẩn hóa 4 trụ cột cốt lõi của Báo cáo tuần: (1) Nhiệm vụ đã hoàn thành, (2) Nhiệm vụ chưa hoàn thành, (3) Khó khăn & vướng mắc, (4) Kiến thức học được. Tự động liên kết các task từ Kanban TM-20, cho phép chỉnh sửa trước khi Mentor công bố đánh giá (`PUBLISHED`). |
| **v1.1.0** | 2026-10-07 | AI Senior Pair-Programmer & User | `TM-21` | Tinh chỉnh & Phân tách phạm vi ticket | **Người dùng xác nhận định hướng phân rã:** Chuyển toàn bộ phân hệ **Mentor xem báo cáo tuần và phản hồi/chấm điểm sang ticket riêng TM-22**. TM-21 tập trung **100% vào trải nghiệm và nghiệp vụ Thực tập sinh tạo, lưu nháp, chỉnh sửa, tra cứu timeline và nộp báo cáo tuần**. Lược bỏ `MentorWeeklyReportController` và endpoint Mentor khỏi TM-21 để giữ tính độc lập và tinh gọn. |

---

## 1. Feature Overview (Tổng Quan Tính Năng)

- **Feature Name:** Thực Tập Sinh Nộp Báo Cáo Tuần (Intern Weekly Report Submission & Task Synthesis).
- **Jira Ticket:** [TM-21](https://robluccibn9935.atlassian.net/browse/TM-21)
- **Tuyên Ngôn Nghiệp Vụ Cốt Lõi (Core Business Statement):**
  > **"BÁO CÁO TUẦN LÀ BẢN TỔNG HỢP TIẾN ĐỘ THỰC TẾ ĐA CHIỀU CỦA THỰC TẬP SINH DỰA TRÊN CÁC ĐẦU VIỆC KANBAN HÀNG NGÀY, LÀ CẦU NỐI ĐẦU VÀO ĐỂ GỬI ĐẾN MENTOR PHỤ TRÁCH"**
  >
  > Trong chu trình quản trị đào tạo:
  > - **Mentor ([TM-19](file:///d:/codegym_final_project/InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md)):** Giao việc chi tiết cho TTS qua `MissionBoard` và `MissionItem`.
  > - **Thực tập sinh ([TM-20](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md)):** Thực thi công việc hàng ngày qua bảng Kanban 3 cột (`TODO` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED`).
  > - **Thực tập sinh ([TM-21](file:///d:/codegym_final_project/InternHub/docs/specs/TM-21-intern-weekly-report-spec.md)):** Cuối mỗi tuần thực tập, TTS tổng hợp các nhiệm vụ đã hoàn thành, nhiệm vụ chưa hoàn thành, nêu khó khăn vướng mắc và kiến thức đã học được thành **Báo Cáo Tuần** gửi cho Mentor.
  > - **Mentor ([TM-22](file:///d:/codegym_final_project/InternHub/docs/specs/TM-22-mentor-review-weekly-report-spec.md)):** Tiếp nhận báo cáo tuần của TTS, xem xét đối chiếu và tiến hành chấm điểm 4 tiêu chí kèm nhận xét phản hồi (phát triển độc lập ở TM-22).

- **Target Microservices:**
  1. `intern-and-program-service` (Port 8082):
     - Quản lý thực thể `InternWeeklyReport` và bảng snapshot nhiệm vụ `InternWeeklyReportTask`.
     - Cung cấp API gợi ý nhiệm vụ từ bảng Kanban TM-20 của tuần đó (`/kanban-tasks`).
     - Cung cấp toàn bộ API CRUD báo cáo tuần cá nhân cho Thực tập sinh (`ROLE_INTERN`).
     - Tích hợp `NotificationEventDispatcher` để bắn sự kiện `REPORT_SUBMITTED` đến Mentor.
  2. `api-gateway` (Port 8080):
     - Định tuyến thông suốt các endpoints `/api/interns/my-weekly-reports/**` về `intern-and-program-service`.

- **Target Users & Roles:**
  - **`ROLE_INTERN` (Người Thực Hiện & Soạn Báo Cáo - Duy Nhất Trong TM-21):**
    - Xem danh sách lịch sử báo cáo các tuần thực tập của mình (Timeline).
    - Soạn thảo, lưu nháp (`DRAFT`), chỉnh sửa và nộp chính thức (`SUBMITTED`) báo cáo tuần.
    - Tự động nạp danh sách task đã xong và chưa xong từ Kanban TM-20.
    - Xem nhận xét và điểm số của Mentor sau khi tuần đó đã được đánh giá.

- **Change Level:** **L3** (Tạo thực thể mới, bảng snapshot liên kết, API nghiệp vụ tuần cho Intern, tích hợp thông báo thời gian thực).

---

## 2. Business Goal & Core Objectives (Mục Tiêu Nghiệp Vụ)

1. **Chuẩn Hóa 4 Trụ Cột Báo Cáo Tuần:** Xóa bỏ tình trạng báo cáo chung chung, định hướng TTS tập trung vào: (1) Nhiệm vụ đã hoàn thành, (2) Nhiệm vụ chưa hoàn thành & lý do, (3) Khó khăn cần hỗ trợ, (4) Kiến thức tích lũy được.
2. **Khai Thác Tối Đa Giá Trị Từ TM-20 (Zero Waste Data):** Dữ liệu công việc trên bảng Kanban TM-20 được tự động tổng hợp vào báo cáo tuần chỉ với 1 thao tác click chuột, giúp TTS tiết kiệm thời gian và đảm bảo số liệu báo cáo luôn trung thực.
3. **Tách Bạch Phân Hệ Rõ Ràng (Clear Boundary Isolation):** TM-21 hoàn toàn tập trung vào việc tạo, soạn thảo, quản lý và nộp báo cáo của TTS. Phân hệ Mentor xem và phản hồi được chuyển hẳn sang TM-22.
4. **Vòng Đời Nộp Báo Cáo An Toàn & Linh Hoạt:** Cho phép TTS lưu nháp (`DRAFT`) nhiều lần, chỉnh sửa sau khi nộp nếu Mentor chưa chốt điểm, và tự động khóa cứng khi Mentor đã công bố đánh giá (`PUBLISHED`).

---

## 3. Scope of Work (Phạm Vi Tính Năng)

### 3.1. Trong phạm vi (In Scope - 100% Thuộc Về Thực Tập Sinh)

- **Quản lý Báo Cáo Tuần Cá Nhân của TTS (`ROLE_INTERN`):**
  - `GET /api/interns/my-weekly-reports`: Lấy danh sách timeline toàn bộ các tuần thực tập (từ Tuần 1 đến `totalWeeks`), kèm trạng thái từng tuần (`DRAFT`, `SUBMITTED`, `REVIEWED`, hoặc `NOT_STARTED`), và điểm số của Mentor nếu đã được review.
  - `GET /api/interns/my-weekly-reports/{weekNumber}`: Xem chi tiết báo cáo tuần cá nhân kèm danh sách tasks đã snapshot và đánh giá của Mentor.
  - `GET /api/interns/my-weekly-reports/{weekNumber}/kanban-tasks`: Lấy danh sách gợi ý các nhiệm vụ từ TM-20 của tuần đó (phân loại thành 2 nhóm: `completedTasks` và `unfinishedTasks`).
  - `POST /api/interns/my-weekly-reports`: Tạo mới hoặc lưu nháp (`DRAFT`) báo cáo tuần.
  - `PUT /api/interns/my-weekly-reports/{weekNumber}`: Cập nhật nội dung báo cáo tuần khi chưa bị khóa.
  - `POST /api/interns/my-weekly-reports/{weekNumber}/submit`: Nộp chính thức (`SUBMITTED`) và kích hoạt thông báo cho Mentor qua `NotificationEventDispatcher`.

- **Bảo Vệ Tính Toàn Vẹn & Khóa Dữ Liệu:**
  - Kiểm tra IDOR: Thực tập sinh chỉ được tạo, xem, sửa báo cáo của chính mình (mapping trực tiếp qua token xác thực).
  - Kiểm tra trạng thái kỳ thực tập: Chỉ TTS có trạng thái `INTERNING` hoặc `APPROVED` mới được nộp báo cáo.
  - Khóa báo cáo khi Mentor đã công bố điểm (`status == PUBLISHED` trên `InternWeeklyAssessment`).

### 3.2. Ngoài phạm vi (Out of Scope - *Ngăn chặn suy diễn sai*)

- **MENTOR XEM BÁO CÁO VÀ CHẤM ĐIỂM/PHẢN HỒI THUỘC VỀ TM-22:** Toàn bộ giao diện và endpoints dành riêng cho Mentor xem báo cáo của TTS và chấm điểm feedback sẽ được triển khai độc lập trong ticket **TM-22**.
- **TTS KHÔNG TỰ CHẤM ĐIỂM CHO CHÍNH MÌNH:** Điểm số các tiêu chí (`technicalScore`, `attitudeScore`...) hoàn toàn thuộc thẩm quyền của Mentor.
- **KHÔNG THAY THẾ BẢNG KANBAN TM-20:** Báo cáo tuần là bản tổng hợp định kỳ cuối tuần, không thay thế việc di chuyển thẻ Kanban hàng ngày của TM-20.
- **Không can thiệp Frontend:** Tuân thủ 100% Rule 7 (Boundary Isolation).
- **Không chạy SQL phá hoại & không tự commit/push Git:** Tuân thủ Rule 8 và Rule 4.

---

## 4. Potential Logic Loopholes & Mitigations (Các Lỗ Hổng Logic & Edge Cases)

### 4.1. Edge Case 1: Lỗ hổng IDOR - TTS nộp hoặc sửa báo cáo của TTS khác
- **Vấn đề:** TTS A gửi request `PUT /api/interns/my-weekly-reports/2` nhưng cố tình thao tác trên dữ liệu hoặc gửi mã TTS của TTS B.
- **Giải pháp:** Service trích xuất trực tiếp `userId` từ `CustomUserDetails` trong Security Context, truy vấn `InternProfile` của chính tài khoản đó. Báo cáo luôn được gắn chặt với `internCode` của user đăng nhập. Không nhận `internCode` từ Client Request Body của Intern.

### 4.2. Edge Case 2: TTS cố tình sửa báo cáo sau khi Mentor đã chấm điểm (Locked by Evaluation)
- **Vấn đề:** Mentor đã chấm điểm và công bố đánh giá (`status = PUBLISHED`), nhưng TTS vẫn gửi request `PUT` hoặc `POST .../submit` để thay đổi nội dung báo cáo nhằm thay đổi chứng cứ.
- **Giải pháp:** Service kiểm tra `assessmentRepository.findByInternCodeAndWeekNumber(internCode, weekNumber)`:

```java
Optional<InternWeeklyAssessment> assessmentOpt = assessmentRepository.findByInternCodeAndWeekNumber(internCode, weekNumber);
if (assessmentOpt.isPresent() && assessmentOpt.get().getStatus() == InternWeeklyAssessment.AssessmentStatus.PUBLISHED) {
    throw new BadRequestException("Báo cáo tuần " + weekNumber + " đã được Mentor đánh giá và công bố điểm, không thể chỉnh sửa.");
}
```

### 4.3. Edge Case 3: Nộp báo cáo cho tuần vượt quá thời gian thực tế (Future Week Submission)
- **Vấn đề:** TTS mới thực tập đến Tuần 2 nhưng cố tình gửi báo cáo cho Tuần 8.
- **Giải pháp:**
  - Tính toán `currentWeek` dựa trên `internProfile.getStartDate()` và ngày hiện tại.
  - Cho phép nộp tối đa là `currentWeek` (hoặc `currentWeek + 1` nếu sát ngày chuyển tuần). Nếu `weekNumber > currentWeek + 1`, ném `BadRequestException("Bạn chưa thể nộp báo cáo cho tuần trong tương lai.")`.
  - Nếu `weekNumber < 1`, ném `BadRequestException("Số tuần không hợp lệ.")`.

### 4.4. Edge Case 4: Trùng lặp báo cáo cho cùng một tuần (Duplicate Weekly Report)
- **Vấn đề:** TTS bấm lưu hoặc tạo mới 2 lần dẫn đến sinh 2 bản ghi báo cáo cho cùng một tuần `weekNumber`.
- **Giải pháp:**
  - Unique Constraint tại database: `@Index(name = "idx_weekly_report_intern_week", columnList = "intern_code, week_number", unique = true)`.
  - Trong logic Service: Khi gọi API lưu/tạo báo cáo, nếu đã tồn tại bản ghi của tuần đó thì thực hiện cập nhật (Upsert logic), không bao giờ tạo thêm bản ghi thứ hai.

### 4.5. Edge Case 5: Đồng bộ danh sách nhiệm vụ từ TM-20 (Task Snapshot Inconsistency)
- **Vấn đề:** Sau khi TTS nộp báo cáo, nếu task trên Kanban TM-20 bị Mentor xóa hoặc đổi tên, báo cáo tuần có thể bị mất dữ liệu hoặc hiển thị sai lệch.
- **Giải pháp:** Bảng `intern_weekly_report_tasks` lưu trữ dưới dạng **Snapshot**:
  - Lưu `task_title`, `task_status` tại thời điểm nộp báo cáo.
  - Dù task gốc sau này có thay đổi hay bị xóa, báo cáo tuần vẫn giữ nguyên nội dung nguyên bản đã nộp.

### 4.6. Edge Case 6: Lỗi N+1 Query JPA khi tải danh sách Báo cáo tuần & Snapshot Tasks
- **Vấn đề:** Khi TTS tải chi tiết báo cáo hoặc tải danh sách timeline, Hibernate thực hiện query riêng lẻ cho từng task đính kèm.
- **Giải pháp:**
  - Phương thức truy vấn chi tiết báo cáo sử dụng `JOIN FETCH report.tasks`.
  - Danh sách timeline tóm tắt (`GET /api/interns/my-weekly-reports`) chỉ truy vấn các trường tóm tắt từ `intern_weekly_reports`, không tải toàn bộ danh sách task con.

---

## 5. Functional Requirements (Yêu Cầu Chức Năng Của Thực Tập Sinh)

- **FR-1 (Tra cứu Timeline Báo Cáo Tuần Cá Nhân):**
  - Trả về danh sách tổng quan các tuần thực tập từ Tuần 1 đến `totalWeeks` (mặc định 12 tuần hoặc tính theo `startDate` - `endDate`).
  - Mỗi tuần hiển thị: số tuần, khoảng ngày (từ ngày - đến ngày), trạng thái báo cáo (`NOT_STARTED`, `DRAFT`, `SUBMITTED`, `REVIEWED`), điểm trung bình và nhận xét của Mentor (nếu đã chấm).

- **FR-2 (Gợi Ý Nhiệm Vụ Từ Bảng Kanban TM-20):**
  - Cung cấp API trích xuất các task của TTS trong phạm vi thời gian của tuần chỉ định:
    - `completedTasks`: Các `MissionItem` mà TTS có trong `assignees` và `status = COMPLETED`.
    - `unfinishedTasks`: Các `MissionItem` mà TTS có trong `assignees` và `status != COMPLETED` (`IN_PROGRESS` hoặc `TODO`).
  - Trả về đầy đủ thông tin: ID task, tiêu đề, trạng thái, hạn chót, link nộp bài (`submissionUrl`) và ghi chú hoàn thành (`completionNote`).

- **FR-3 (Tạo / Lưu Nháp Báo Cáo Tuần):**
  - TTS có thể lưu nháp báo cáo với trạng thái `DRAFT` bất kỳ lúc nào để hoàn thiện dần.
  - Hỗ trợ lưu 4 trụ cột cốt lõi:
    1. Tóm tắt công việc hoàn thành (`completedTasksSummary`).
    2. Tóm tắt công việc chưa hoàn thành & lý do (`unfinishedTasksSummary`).
    3. Khó khăn, vướng mắc (`difficultiesAndChallenges`).
    4. Kiến thức học được (`learningsAndKnowledge`).
    5. Kế hoạch tuần tới (`nextWeekPlan`).
    6. Link tài liệu đính kèm (`reportAttachmentUrl`).
    7. Danh sách các task được chọn đưa vào báo cáo (`taskList`).

- **FR-4 (Nộp Chính Thức Báo Cáo Tuần):**
  - TTS bấm nộp báo cáo: Hệ thống kiểm tra các trường bắt buộc (`completedTasksSummary` không được rỗng), chuyển trạng thái sang `SUBMITTED`, ghi nhận `submittedAt = now()`.
  - Tự động kích hoạt sự kiện gửi thông báo đến Mentor phụ trách: *"TTS {internName} đã nộp báo cáo tuần {weekNumber}."*

- **FR-5 (Chỉnh Sửa Báo Cáo Tuần Khi Chưa Bị Khóa):**
  - Nếu Mentor chưa công bố đánh giá (`status != PUBLISHED`), TTS có thể cập nhật lại nội dung báo cáo.
  - Sau khi cập nhật, thời gian `updatedAt` được làm mới.

---

## 6. Business Rules (Quy Tắc Nghiệp Vụ)

- **BR-1 (Ràng Buộc Trạng Thái Hoạt Động Của TTS):** Chỉ TTS có trạng thái hồ sơ là `INTERNING` hoặc `APPROVED` mới được phép thao tác nộp/chỉnh sửa báo cáo tuần.
- **BR-2 (Ràng Buộc Tuần Hợp Lệ):** `weekNumber` phải là số nguyên dương ($1 \le weekNumber \le totalWeeks$) và không được vượt quá `currentWeek + 1`.
- **BR-3 (Quy Tắc Khóa Báo Cáo - Immutability upon Assessment):** Khi Mentor đã công bố đánh giá tuần (`InternWeeklyAssessment.status == PUBLISHED`), báo cáo tuần tương ứng sẽ chuyển sang trạng thái `REVIEWED` và bị khóa vĩnh viễn (Read-only).
- **BR-4 (Độc Lập Dữ Liệu & Quyền Hạn):** TTS sở hữu và chỉnh sửa bảng `intern_weekly_reports`. Mentor sở hữu và chấm điểm trên bảng `intern_weekly_assessments`. Không can thiệp chéo quyền dữ liệu của nhau.
- **BR-5 (Thông Báo Tự Động Đến Mentor):** Thao tác Nộp báo cáo tuần (`SUBMITTED`) kích hoạt dispatch thông báo đến `mentorId` của TTS qua `NotificationEventDispatcher` với mã sự kiện `REPORT_SUBMITTED`.

---

## 7. Data Model (Mô Hình Dữ Liệu)

> [!NOTE]
> **Tuân thủ triệt để Rule 31 (Reuse First, Zero Unnecessary Redundancy):**
> - Tái sử dụng `InternProfile` để định danh và liên kết Mentor.
> - Tái sử dụng `MissionItem` (TM-20) làm nguồn dữ liệu nạp task.
> - Tái sử dụng `InternWeeklyAssessment` làm mắt xích chấm điểm của Mentor.
> - Xây dựng 2 bảng mới được tối ưu hóa chỉ mục (Index) đầy đủ.

### 7.1. Script Khởi Tạo Bảng CSDL (MySQL DDL)

```sql
-- 1. Bảng lưu trữ Báo cáo tuần của Thực tập sinh
CREATE TABLE IF NOT EXISTS intern_weekly_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intern_code VARCHAR(50) NOT NULL,
    mentor_id BIGINT NOT NULL,
    week_number INT NOT NULL,
    report_date DATE NOT NULL,
    completed_tasks_summary TEXT NOT NULL COMMENT 'Tổng hợp các công việc đã hoàn thành trong tuần',
    unfinished_tasks_summary TEXT NULL COMMENT 'Tổng hợp các công việc chưa hoàn thành & lý do',
    difficulties_and_challenges TEXT NULL COMMENT 'Khó khăn, vướng mắc kỹ thuật & quy trình',
    learnings_and_knowledge TEXT NULL COMMENT 'Kiến thức, bài học và kỹ năng học được',
    next_week_plan TEXT NULL COMMENT 'Mục tiêu & kế hoạch dự kiến cho tuần tới',
    report_attachment_url VARCHAR(500) NULL COMMENT 'Đường link tài liệu, sản phẩm, PR đính kèm',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, SUBMITTED, REVIEWED',
    submitted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NULL,
    updated_by VARCHAR(100) NULL,
    CONSTRAINT uq_weekly_report_intern_week UNIQUE (intern_code, week_number),
    INDEX idx_weekly_report_intern (intern_code),
    INDEX idx_weekly_report_mentor_status (mentor_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Bảng lưu snapshot các nhiệm vụ liên kết với Báo cáo tuần
CREATE TABLE IF NOT EXISTS intern_weekly_report_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    mission_item_id BIGINT NULL COMMENT 'ID của MissionItem gốc (TM-20)',
    task_title VARCHAR(200) NOT NULL COMMENT 'Tiêu đề nhiệm vụ tại thời điểm báo cáo',
    task_status VARCHAR(20) NOT NULL COMMENT 'COMPLETED, IN_PROGRESS, TODO',
    submission_url VARCHAR(500) NULL COMMENT 'Link nộp bài tại thời điểm báo cáo',
    note TEXT NULL COMMENT 'Ghi chú cụ thể của TTS về nhiệm vụ này',
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NULL,
    updated_by VARCHAR(100) NULL,
    INDEX idx_report_tasks_report_id (report_id),
    CONSTRAINT fk_report_tasks_report FOREIGN KEY (report_id) REFERENCES intern_weekly_reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 7.2. JPA Entity Mapping

#### 1. Thực thể `InternWeeklyReport.java`

```java
package org.example.internservice.intern.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.internservice.common.entity.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "intern_weekly_reports",
    indexes = {
        @Index(name = "idx_weekly_report_intern", columnList = "intern_code"),
        @Index(name = "idx_weekly_report_mentor_status", columnList = "mentor_id, status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_weekly_report_intern_week", columnNames = {"intern_code", "week_number"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternWeeklyReport extends BaseEntity {

    @Column(name = "intern_code", nullable = false, length = 50)
    private String internCode;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "completed_tasks_summary", nullable = false, columnDefinition = "TEXT")
    private String completedTasksSummary;

    @Column(name = "unfinished_tasks_summary", columnDefinition = "TEXT")
    private String unfinishedTasksSummary;

    @Column(name = "difficulties_and_challenges", columnDefinition = "TEXT")
    private String difficultiesAndChallenges;

    @Column(name = "learnings_and_knowledge", columnDefinition = "TEXT")
    private String learningsAndKnowledge;

    @Column(name = "next_week_plan", columnDefinition = "TEXT")
    private String nextWeekPlan;

    @Column(name = "report_attachment_url", length = 500)
    private String reportAttachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WeeklyReportStatus status = WeeklyReportStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InternWeeklyReportTask> tasks = new ArrayList<>();

    public void addTask(InternWeeklyReportTask task) {
        tasks.add(task);
        task.setReport(this);
    }

    public void clearTasks() {
        tasks.clear();
    }

    public enum WeeklyReportStatus {
        DRAFT,
        SUBMITTED,
        REVIEWED
    }
}
```

#### 2. Thực thể `InternWeeklyReportTask.java`

```java
package org.example.internservice.intern.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.internservice.common.entity.BaseEntity;

@Entity
@Table(
    name = "intern_weekly_report_tasks",
    indexes = {
        @Index(name = "idx_report_tasks_report_id", columnList = "report_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternWeeklyReportTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private InternWeeklyReport report;

    @Column(name = "mission_item_id")
    private Long missionItemId;

    @Column(name = "task_title", nullable = false, length = 200)
    private String taskTitle;

    @Column(name = "task_status", nullable = false, length = 20)
    private String taskStatus;

    @Column(name = "submission_url", length = 500)
    private String submissionUrl;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;
}
```

---

## 8. API Contract (Đặc Tả Giao Tiếp REST API)

> [!NOTE]
> Tuân thủ Rule 18 (RESTful chuẩn hóa), Rule 19 (Đóng gói 100% trong `ResponseEntity<ApiResponse<T>>`), Rule 16 (Phân tách Request & Response DTOs).

### 8.1. Danh Mục Toàn Bộ Endpoints Cho TM-21 (Dành Riêng Cho Thực Tập Sinh)

| STT | HTTP Method | Endpoint | Quyền (PreAuthorize) | Trách nhiệm nghiệp vụ |
| :---: | :---: | :--- | :---: | :--- |
| **1** | `GET` | `/api/interns/my-weekly-reports` | `ROLE_INTERN` | Lấy danh sách timeline toàn bộ các tuần thực tập của TTS đăng nhập |
| **2** | `GET` | `/api/interns/my-weekly-reports/{weekNumber}` | `ROLE_INTERN` | Lấy chi tiết báo cáo của một tuần cụ thể của TTS (kèm đánh giá của Mentor nếu có) |
| **3** | `GET` | `/api/interns/my-weekly-reports/{weekNumber}/kanban-tasks` | `ROLE_INTERN` | Lấy gợi ý danh sách nhiệm vụ từ bảng Kanban TM-20 của tuần đó |
| **4** | `POST` | `/api/interns/my-weekly-reports` | `ROLE_INTERN` | Lưu nháp (`DRAFT`) hoặc nộp mới báo cáo tuần |
| **5** | `PUT` | `/api/interns/my-weekly-reports/{weekNumber}` | `ROLE_INTERN` | Chỉnh sửa nội dung báo cáo tuần (khi Mentor chưa công bố đánh giá) |
| **6** | `POST` | `/api/interns/my-weekly-reports/{weekNumber}/submit` | `ROLE_INTERN` | Nộp chính thức (`SUBMITTED`) báo cáo tuần & bắn thông báo cho Mentor |

*(Lưu ý: Các endpoints phục vụ Mentor xem danh sách báo cáo tuần của TTS sẽ được định nghĩa và triển khai ở TM-22).*

---

### 8.2. Chi Tiết Payloads Mẫu Chuẩn Hóa

#### 1. Lấy danh sách timeline báo cáo tuần: `GET /api/interns/my-weekly-reports`

**Response Success (200 OK):**

```json
{
  "code": 200,
  "success": true,
  "message": "Lấy danh sách báo cáo tuần thành công",
  "data": {
    "currentWeek": 3,
    "totalWeeks": 12,
    "reports": [
      {
        "weekNumber": 1,
        "startDate": "2026-09-21",
        "endDate": "2026-09-27",
        "status": "REVIEWED",
        "statusDisplayName": "Đã được đánh giá",
        "submittedAt": "2026-09-26T17:30:00",
        "mentorAverageScore": 4.5,
        "mentorFeedback": "Tiếp thu kiến trúc nhanh, hoàn thành tốt task cài đặt môi trường."
      },
      {
        "weekNumber": 2,
        "startDate": "2026-09-28",
        "endDate": "2026-10-04",
        "status": "SUBMITTED",
        "statusDisplayName": "Đã nộp, chờ đánh giá",
        "submittedAt": "2026-10-03T18:15:00",
        "mentorAverageScore": null,
        "mentorFeedback": null
      },
      {
        "weekNumber": 3,
        "startDate": "2026-10-05",
        "endDate": "2026-10-11",
        "status": "DRAFT",
        "statusDisplayName": "Bản nháp",
        "submittedAt": null,
        "mentorAverageScore": null,
        "mentorFeedback": null
      }
    ]
  }
}
```

---

#### 2. Lấy gợi ý nhiệm vụ từ Kanban TM-20: `GET /api/interns/my-weekly-reports/{weekNumber}/kanban-tasks`

**Response Success (200 OK):**

```json
{
  "code": 200,
  "success": true,
  "message": "Lấy danh sách nhiệm vụ Kanban gợi ý thành công",
  "data": {
    "weekNumber": 3,
    "completedTasks": [
      {
        "missionItemId": 101,
        "title": "Thiết kế Entity và Migration cho TM-20 Kanban",
        "status": "COMPLETED",
        "submissionUrl": "https://github.com/internhub/repo/pull/45",
        "completionNote": "Đã hoàn thành và test pass 8/8 unit tests",
        "submittedAt": "2026-10-06T14:20:00"
      }
    ],
    "unfinishedTasks": [
      {
        "missionItemId": 102,
        "title": "Tối ưu hóa Distributed Cache với Redis",
        "status": "IN_PROGRESS",
        "dueDate": "2026-10-10",
        "isOverdue": false
      }
    ]
  }
}
```

---

#### 3. Tạo mới hoặc Lưu nháp báo cáo tuần: `POST /api/interns/my-weekly-reports`

**Request Body (`SaveWeeklyReportRequest`):**

```json
{
  "weekNumber": 3,
  "reportDate": "2026-10-07",
  "completedTasksSummary": "- Hoàn thành task thiết kế Entity và Migration cho TM-20 Kanban\n- Đã viết unit test đạt độ phủ 100%",
  "unfinishedTasksSummary": "- Task Redis Cache đang nghiên cứu do lỗi serialization",
  "difficultiesAndChallenges": "Gặp khó khăn khi tích hợp Redis Cluster trên Docker Windows",
  "learningsAndKnowledge": "Hiểu sâu hơn về Spring Data Redis và kiến trúc Cache-Aside",
  "nextWeekPlan": "Hoàn tất module Cache và bắt tay vào Báo cáo tuần TM-21",
  "reportAttachmentUrl": "https://docs.google.com/document/d/xyz-sample-report",
  "isSubmit": false,
  "tasks": [
    {
      "missionItemId": 101,
      "taskTitle": "Thiết kế Entity và Migration cho TM-20 Kanban",
      "taskStatus": "COMPLETED",
      "submissionUrl": "https://github.com/internhub/repo/pull/45",
      "note": "Đã merge code vào nhánh develop",
      "isCompleted": true
    },
    {
      "missionItemId": 102,
      "taskTitle": "Tối ưu hóa Distributed Cache với Redis",
      "taskStatus": "IN_PROGRESS",
      "submissionUrl": null,
      "note": "Còn vướng lỗi kết nối",
      "isCompleted": false
    }
  ]
}
```

**Response Success (200 OK):**

```json
{
  "code": 200,
  "success": true,
  "message": "Lưu nháp báo cáo tuần thành công",
  "data": {
    "id": 15,
    "internCode": "TTS-2026-001",
    "weekNumber": 3,
    "status": "DRAFT",
    "statusDisplayName": "Bản nháp",
    "reportDate": "2026-10-07",
    "completedTasksSummary": "- Hoàn thành task thiết kế Entity và Migration...",
    "unfinishedTasksSummary": "- Task Redis Cache đang nghiên cứu...",
    "difficultiesAndChallenges": "Gặp khó khăn khi tích hợp Redis Cluster...",
    "learningsAndKnowledge": "Hiểu sâu hơn về Spring Data Redis...",
    "nextWeekPlan": "Hoàn tất module Cache...",
    "reportAttachmentUrl": "https://docs.google.com/document/d/xyz-sample-report",
    "submittedAt": null,
    "tasksCount": 2,
    "mentorAssessment": null
  }
}
```

---

#### 4. Nộp chính thức báo cáo tuần: `POST /api/interns/my-weekly-reports/{weekNumber}/submit`

**Response Success (200 OK):**

```json
{
  "code": 200,
  "success": true,
  "message": "Nộp báo cáo tuần thành công. Thông báo đã được gửi đến Mentor phụ trách.",
  "data": {
    "id": 15,
    "internCode": "TTS-2026-001",
    "weekNumber": 3,
    "status": "SUBMITTED",
    "statusDisplayName": "Đã nộp, chờ đánh giá",
    "submittedAt": "2026-10-07T10:15:00"
  }
}
```

---

#### 5. Xem chi tiết báo cáo tuần của TTS: `GET /api/interns/my-weekly-reports/{weekNumber}`

**Response Success (200 OK):**

```json
{
  "code": 200,
  "success": true,
  "message": "Lấy chi tiết báo cáo tuần thành công",
  "data": {
    "id": 15,
    "internCode": "TTS-2026-001",
    "weekNumber": 3,
    "status": "SUBMITTED",
    "statusDisplayName": "Đã nộp, chờ đánh giá",
    "reportDate": "2026-10-07",
    "completedTasksSummary": "- Hoàn thành task thiết kế Entity và Migration cho TM-20 Kanban\n- Đã viết unit test đạt độ phủ 100%",
    "unfinishedTasksSummary": "- Task Redis Cache đang nghiên cứu do lỗi serialization",
    "difficultiesAndChallenges": "Gặp khó khăn khi tích hợp Redis Cluster trên Docker Windows",
    "learningsAndKnowledge": "Hiểu sâu hơn về Spring Data Redis và kiến trúc Cache-Aside",
    "nextWeekPlan": "Hoàn tất module Cache và bắt tay vào Báo cáo tuần TM-21",
    "reportAttachmentUrl": "https://docs.google.com/document/d/xyz-sample-report",
    "submittedAt": "2026-10-07T10:15:00",
    "tasks": [
      {
        "id": 41,
        "missionItemId": 101,
        "taskTitle": "Thiết kế Entity và Migration cho TM-20 Kanban",
        "taskStatus": "COMPLETED",
        "submissionUrl": "https://github.com/internhub/repo/pull/45",
        "note": "Đã merge code vào nhánh develop",
        "isCompleted": true
      },
      {
        "id": 42,
        "missionItemId": 102,
        "taskTitle": "Tối ưu hóa Distributed Cache với Redis",
        "taskStatus": "IN_PROGRESS",
        "submissionUrl": null,
        "note": "Còn vướng lỗi kết nối",
        "isCompleted": false
      }
    ],
    "mentorAssessment": null
  }
}
```

---

## 9. End-to-End Sequence & Data Flow (Luồng Tương Tác Trực Quan Phân Hệ TM-21)

```text
┌──────────────┐                 ┌───────────────────────┐                 ┌─────────────────┐
│ Thực Tập Sinh│                 │ intern-and-program    │                 │ Mentor Hướng Dẫn│
└──────┬───────┘                 └──────────┬────────────┘                 └────────┬────────┘
       │                                    │                                       │
       │ 1. Mở màn hình Báo Cáo Tuần        │                                       │
       ├───────────────────────────────────►│                                       │
       │    GET /api/interns/my-reports     │                                       │
       │◄───────────────────────────────────┤                                       │
       │    Trả về Timeline các tuần        │                                       │
       │                                    │                                       │
       │ 2. Lấy gợi ý công việc Kanban      │                                       │
       ├───────────────────────────────────►│ (Query MissionItem từ TM-20)           │
       │    GET /.../{weekNo}/kanban-tasks  │                                       │
       │◄───────────────────────────────────┤                                       │
       │    Trả về tasks COMPLETED & TODO   │                                       │
       │                                    │                                       │
       │ 3. Soạn nội dung & Nộp báo cáo     │                                       │
       ├───────────────────────────────────►│ Lưu InternWeeklyReport (SUBMITTED)    │
       │    POST /.../{weekNo}/submit       │ Dispatch Event REPORT_SUBMITTED       │
       │◄───────────────────────────────────┤───────► Gửi Realtime Notification ────┼──► Nhận thông báo:
       │    Nộp thành công                  │         qua notification-service      │    "TTS vừa nộp báo cáo!"
       │                                    │                                       │
       │                                    │                                       │  (Chờ Mentor đánh giá
       │                                    │                                       │   tại phân hệ TM-22)
       │                                    │                                       │
```

---

## 10. Non-Functional Requirements & Constraints (Yêu Cầu Phi Chức Năng)

1. **Phòng Chống Triệt Để Lỗi N+1 Query JPA (Rule 22):**
   - Khi load chi tiết báo cáo tuần kèm danh sách tasks snapshot, sử dụng `JOIN FETCH report.tasks` trong `InternWeeklyReportRepository`.
2. **Tiêu Chuẩn Anti-God-Class (Rule 17):**
   - Tạo mới phân hệ riêng biệt: `InternWeeklyReportService` & `InternWeeklyReportController`, không gộp nhồi nhét vào `WeeklyAssessmentServiceImpl`.
3. **Quản Lý Giao Dịch An Toàn (`@Transactional`) (Rule 23):**
   - Mặc định `@Transactional(readOnly = true)` ở cấp độ class `InternWeeklyReportServiceImpl`.
   - Gắn `@Transactional` tường minh tại các hàm ghi: `saveReport`, `submitReport`.
4. **Constructor Injection An Toàn (Rule 21):**
   - Sử dụng `@RequiredArgsConstructor` từ Lombok, không dùng `@Autowired` trên field.
5. **Kiểm Tra Ranh Giới Quyền Hạn (Boundary Isolation - Rule 7):**
   - Không đụng chạm hoặc chỉnh sửa bất kỳ file nào thuộc `InternHub-Frontend/` trong suốt quá trình phát triển Backend TM-21.

---

## 11. Acceptance Criteria Checklist (Tiêu Chí Chấp Nhận)

- [ ] **AC-1 (Tra Cứu Timeline Tuần Cá Nhân):** TTS đăng nhập lấy được danh sách timeline từ Tuần 1 đến `totalWeeks` kèm trạng thái nộp và điểm đánh giá của từng tuần.
- [ ] **AC-2 (Gợi Ý Task Kanban Chuẩn Xác):** API `/kanban-tasks` trích xuất đúng các task của TTS phân công từ TM-20 trong tuần (tách bạch rõ 2 nhóm: Hoàn thành và Chưa hoàn thành).
- [ ] **AC-3 (Lưu Nháp Thành Công):** TTS lưu nháp báo cáo với trạng thái `DRAFT`, CSDL lưu lại dữ liệu và chưa kích hoạt thông báo cho Mentor.
- [ ] **AC-4 (Nộp Chính Thức & Bắn Thông Báo):** TTS nộp báo cáo thành công (`SUBMITTED`), thời gian nộp `submittedAt` được ghi nhận, Mentor phụ trách nhận được thông báo thời gian thực qua hệ thống.
- [ ] **AC-5 (Chặn Chỉnh Sửa Khi Đã Có Điểm Đánh Giá):** Nếu Mentor đã công bố điểm (`status = PUBLISHED` trên `InternWeeklyAssessment`), mọi request cập nhật báo cáo đều bị từ chối với lỗi `400 BAD_REQUEST`.
- [ ] **AC-6 (Bảo Vệ Chống Lỗ Hổng IDOR):** TTS không thể xem hoặc sửa báo cáo tuần của TTS khác, ném lỗi `403 FORBIDDEN` hoặc tự động mapping theo session đăng nhập.

---

## 12. Unit & Integration Test Cases Checklist

### 12.1. Unit Tests (`InternWeeklyReportServiceTest`)

- [ ] **UT-BE-01:** `getMyReportsTimeline_Success`: Lấy danh sách timeline các tuần cho TTS thành công.
- [ ] **UT-BE-02:** `getSuggestedKanbanTasks_Success`: Lấy gợi ý nhiệm vụ từ TM-20 phân loại đúng 2 nhóm `completed` và `unfinished`.
- [ ] **UT-BE-03:** `saveReport_Success_Draft`: Lưu nháp báo cáo tuần với trạng thái `DRAFT` thành công, lưu đúng snapshot tasks.
- [ ] **UT-BE-04:** `submitReport_Success`: Nộp báo cáo tuần thành công, chuyển sang `SUBMITTED`, kiểm tra dispatch notification cho Mentor.
- [ ] **UT-BE-05:** `updateReport_Fail_WhenMentorPublished`: Ném `BadRequestException` khi cố tình sửa báo cáo đã được Mentor công bố đánh giá.
- [ ] **UT-BE-06:** `saveReport_Fail_FutureWeek`: Ném `BadRequestException` khi nộp tuần trong tương lai vượt quá `currentWeek + 1`.

### 12.2. Integration Tests (`InternWeeklyReportControllerIT`)

- [ ] **IT-BE-01:** Kiểm thử qua MockMvc: TTS đăng nhập gọi `GET /api/interns/my-weekly-reports` thành công (HTTP 200).
- [ ] **IT-BE-02:** Kiểm thử qua MockMvc: TTS nộp báo cáo thành công (HTTP 200).
- [ ] **IT-BE-03:** Kiểm thử qua MockMvc: Chặn người dùng chưa xác thực (HTTP 401).

---

## 13. Implementation Checklist (Danh Sách File & Hạng Mục Triển Khai TM-21)

### 13.1. Entity & Repository
- [ ] `intern/entity/InternWeeklyReport.java` (Entity lưu báo cáo tuần)
- [ ] `intern/entity/InternWeeklyReportTask.java` (Entity lưu snapshot nhiệm vụ tuần)
- [ ] `intern/repository/InternWeeklyReportRepository.java` (Repository thao tác báo cáo tuần)
- [ ] `intern/repository/InternWeeklyReportTaskRepository.java` (Repository thao tác snapshot tasks)

### 13.2. DTOs
- [ ] `intern/dto/request/SaveWeeklyReportRequest.java` (Request body tạo/lưu nháp/nộp báo cáo)
- [ ] `intern/dto/response/WeeklyReportTimelineResponse.java` (Response danh sách timeline các tuần)
- [ ] `intern/dto/response/WeeklyReportDetailResponse.java` (Response chi tiết báo cáo tuần kèm tasks & mentor feedback)
- [ ] `intern/dto/response/SuggestedKanbanTasksResponse.java` (Response danh sách nhiệm vụ Kanban gợi ý)

### 13.3. Service Layer
- [ ] `intern/service/InternWeeklyReportService.java` (Interface nghiệp vụ Báo cáo tuần cho TTS)
- [ ] `intern/service/impl/InternWeeklyReportServiceImpl.java` (Cài đặt nghiệp vụ: timeline, gợi ý Kanban, lưu nháp, nộp, kiểm tra khóa, dispatch notification)

### 13.4. Controller Layer
- [ ] `intern/controller/InternWeeklyReportController.java` (Controller duy nhất cho TTS: `/api/interns/my-weekly-reports/**`)

### 13.5. Automated Tests
- [ ] `src/test/java/org/example/internservice/intern/service/InternWeeklyReportServiceTest.java` (Unit tests)
