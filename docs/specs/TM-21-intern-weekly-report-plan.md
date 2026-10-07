# Kế Hoạch Triển Khai Kỹ Thuật (Technical Implementation Plan) - TM-21

> **Tính năng:** Thực Tập Sinh - Nộp Báo Cáo Tuần (Intern Weekly Report Submission & Task Synthesis)  
> **Jira Ticket:** [TM-21](https://robluccibn9935.atlassian.net/browse/TM-21)  
> **Tài liệu đặc tả (Spec):** [TM-21-intern-weekly-report-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-21-intern-weekly-report-spec.md)  
> **Đề xuất nghiệp vụ đã duyệt:** [TM-21-business-proposal.md](file:///C:/Users/admin/.gemini/antigravity-ide/brain/6f23f4e4-cd13-4ddc-af3e-62b61766f750/TM-21-business-proposal.md)  
> **Tiếp nối phía sau:** [TM-22 (Mentor xem báo cáo tuần & Đánh giá phản hồi)](file:///d:/codegym_final_project/InternHub/docs/specs/TM-22-mentor-review-weekly-report-spec.md)  
> **Trạng thái:** PROPOSED / CHỜ PHÊ DUYỆT (PENDING APPROVAL)  
> **Cấp độ thay đổi (Change Level):** **L3** (Tạo mới Entities, Repositories, DTOs, Service Layer độc lập, Controller endpoints cho Intern, tích hợp trích xuất nhiệm vụ Kanban TM-20, và dispatch thông báo cho Mentor).  
> **Service phụ trách:** `intern-and-program-service` (Port 8082), `api-gateway` (Port 8080).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc bất biến của Backend).

---

## 1. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng Mã Nguồn (MANDATORY DIRECTIVE - Rule 31)

> [!CAUTION]
> **CHỈ THỊ CỐT LÕI (RULE 31): "ĐẢM BẢO SẼ QUÉT DỰ ÁN, TRÁNH VIỆC TẠO THÊM CODE MỚI KHÔNG CẦN THIẾT, SỬ DỤNG TỐI ĐA NHỮNG GÌ ĐÃ CÓ ĐỂ PHÁT TRIỂN"**

### 1.1. Rà soát hiện trạng mã nguồn & Database Schema
1. **Các thực thể & bảng CSDL hiện hữu:**
   - [InternProfile.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternProfile.java) (`intern_profiles`): Chứa `startDate`, `endDate`, `mentorId`, `mentorName`, `programId`, `status`.
   - [MissionItem.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/mission/entity/MissionItem.java) (`mission_items`): Chứa thông tin công việc Kanban của TM-20: `status` (`TODO`, `IN_PROGRESS`, `COMPLETED`), `dueDate`, `submittedAt`, `submissionUrl`, `completionNote`.
   - [InternWeeklyAssessment.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternWeeklyAssessment.java) (`intern_weekly_assessments`): Chứa điểm số và nhận xét của Mentor theo `week_number`.
   - [WeeklyAssessmentService.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/WeeklyAssessmentService.java): Đã có sẵn thuật toán tính `currentWeek` dựa trên `ChronoUnit.DAYS.between(startDate, LocalDate.now()) / 7 + 1`.
   - [NotificationEventDispatcher.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/client/NotificationEventDispatcher.java): Cơ chế bắn thông báo thời gian thực nội bộ.

### 1.2. Đánh giá tái sử dụng & Quyết định thiết kế
- **Tái sử dụng 100%:**
  - Tái sử dụng thuật toán tính số tuần từ `startDate` của `WeeklyAssessmentServiceImpl`.
  - Tái sử dụng bảng `mission_items` làm nguồn trích xuất dữ liệu công việc trong tuần của TTS (không tạo thêm bảng lưu task riêng lẻ).
  - Tái sử dụng `NotificationEventDispatcher` để bắn sự kiện `REPORT_SUBMITTED` cho Mentor phụ trách.
- **Tạo mới có kiểm soát (Zero God Class - Rule 17):**
  - Tạo mới thực thể `InternWeeklyReport` (`intern_weekly_reports`) và bảng snapshot `InternWeeklyReportTask` (`intern_weekly_report_tasks`) để phân quyền sạch sẽ giữa TTS và Mentor.
  - Tách riêng service `InternWeeklyReportService` và controller `InternWeeklyReportController` độc lập với `WeeklyAssessmentService` để tránh file phình to quá 300 dòng.
- **Ràng buộc an toàn dữ liệu:**
  - Nếu Mentor đã đánh giá tuần (`InternWeeklyAssessment.status == PUBLISHED`), hệ thống khóa quyền sửa của TTS để bảo vệ tính toàn vẹn của hồ sơ đánh giá.
- **Phân định rõ ranh giới TM-21 và TM-22:**
  - TM-21: 100% Thực tập sinh tạo, lưu nháp, chỉnh sửa, xem timeline và nộp báo cáo tuần cá nhân.
  - TM-22: Mentor tra cứu báo cáo tuần của TTS và thực hiện chấm điểm/phản hồi.

---

## 2. Mục Tiêu Kỹ Thuật & Tuyên Ngôn Nghiệp Vụ

> **ĐỊNH NGHĨA CỐT LÕI: "BÁO CÁO TUẦN LÀ BẢN TỔNG HỢP TIẾN ĐỘ THỰC TẾ ĐA CHIỀU CỦA THỰC TẬP SINH DỰA TRÊN CÁC ĐẦU VIỆC KANBAN HÀNG NGÀY, LÀ CẦU NỐI ĐẦU VÀO ĐỂ GỬI ĐẾN MENTOR PHỤ TRÁCH"**

1. **Tra cứu Timeline các tuần cá nhân:** API `GET /api/interns/my-weekly-reports` hiển thị tiến độ từ Tuần 1 đến `totalWeeks` kèm trạng thái từng tuần.
2. **Gợi ý nhiệm vụ từ Kanban TM-20:** API `GET /api/interns/my-weekly-reports/{weekNumber}/kanban-tasks` tự động gom các task đã xong và chưa xong của TTS trong tuần.
3. **Lưu nháp & Nộp chính thức an toàn:** API `POST /api/interns/my-weekly-reports` và `POST .../{weekNumber}/submit` hỗ trợ lưu 4 trụ cột nghiệp vụ (Đã xong, Chưa xong, Khó khăn, Kiến thức học được).
4. **Bắn thông báo thời gian thực đến Mentor:** Khi TTS bấm nộp báo cáo, hệ thống tự động bắn thông báo `REPORT_SUBMITTED` đến `mentorId` tương ứng.

---

## 3. Kế Hoạch 7 Bước Triển Khai Chi Tiết (7-Step Technical Workflow)

```text
[Bước 1: Khởi tạo Entities JPA] ➔ [Bước 2: Xây dựng DTOs] ➔ [Bước 3: Repositories (JOIN FETCH)]
        ↓
[Bước 4: Service Layer] ➔ [Bước 5: Controller Layer] ➔ [Bước 6: Gateway & Swagger]
        ↓
[Bước 7: Unit Tests & Gradle Compile Check]
```

### Bước 1: Khởi tạo JPA Entities
- **Tập tin:**
  * `org.example.internservice.intern.entity.InternWeeklyReport.java`
  * `org.example.internservice.intern.entity.InternWeeklyReportTask.java`
- Kế thừa `BaseEntity`, khai báo indexes và unique constraint trên `(intern_code, week_number)`.
- Tuân thủ Rule 25: Dùng Lombok an toàn (`@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`), không dùng `@Data`.

### Bước 2: Xây dựng Request & Response DTOs
- **Tập tin:**
  * `intern/dto/request/SaveWeeklyReportRequest.java`: Chứa các trường thông tin tuần, 4 trụ cột text, link tài liệu và danh sách task con. Có Bean Validation (`@NotNull`, `@NotBlank`, `@Size`).
  * `intern/dto/response/WeeklyReportTimelineResponse.java`: Response cho timeline các tuần.
  * `intern/dto/response/WeeklyReportDetailResponse.java`: Response chi tiết báo cáo kèm tasks và mentor assessment.
  * `intern/dto/response/SuggestedKanbanTasksResponse.java`: Response danh sách gợi ý tasks từ TM-20.

### Bước 3: Xây dựng Repositories
- **Tập tin:**
  * `intern/repository/InternWeeklyReportRepository.java`
  * `intern/repository/InternWeeklyReportTaskRepository.java`
- Khai báo các query tối ưu:
  * `findByInternCodeOrderByWeekNumberAsc(String internCode)`: Tải danh sách tóm tắt timeline.
  * `findByInternCodeAndWeekNumber(String internCode, Integer weekNumber)`: Tải chi tiết báo cáo.
  * Phương thức `findByInternCodeAndWeekNumberWithTasks`: Sử dụng `LEFT JOIN FETCH report.tasks` chống N+1 query (Rule 22).

### Bước 4: Xây dựng Service Layer
- **Tập tin:**
  * `intern/service/InternWeeklyReportService.java` (Interface)
  * `intern/service/impl/InternWeeklyReportServiceImpl.java` (Implementation)
- **Nghiệp vụ chi tiết:**
  * `getTimeline(Long userId)`: Lấy `InternProfile`, tính `currentWeek` và `totalWeeks`, map danh sách các tuần với trạng thái và điểm Mentor.
  * `getSuggestedKanbanTasks(Long userId, Integer weekNumber)`: Lấy các `MissionItem` được phân công cho TTS, phân loại theo `COMPLETED` và `UNFINISHED`.
  * `saveOrUpdateReport(Long userId, SaveWeeklyReportRequest request)`: Kiểm tra trạng thái kỳ thực tập, kiểm tra tuần hợp lệ, kiểm tra xem Mentor đã publish điểm chưa, upsert báo cáo và lưu snapshot tasks.
  * `submitReport(Long userId, Integer weekNumber)`: Kiểm tra điều kiện nộp, chuyển trạng thái sang `SUBMITTED`, ghi nhận `submittedAt = now()`, dispatch notification cho Mentor.
  * `getMyReportDetail(Long userId, Integer weekNumber)`: TTS xem chi tiết báo cáo của mình.

### Bước 5: Xây dựng Controller Layer
- **Tập tin:**
  * `intern/controller/InternWeeklyReportController.java`: Phục vụ duy nhất `ROLE_INTERN` (`/api/interns/my-weekly-reports/**`).
- Đóng gói toàn bộ kết quả qua `ResponseEntity<ApiResponse<T>>` (Rule 19).
- Bổ sung Swagger annotations (`@Tag`, `@Operation`) (Rule 18).

### Bước 6: Kiểm tra định tuyến Gateway & Bảo mật
- **Tập tin:**
  * `api-gateway`: Đảm bảo định tuyến `/api/interns/my-weekly-reports/**` thông suốt về `intern-and-program-service`.
- Kiểm tra ma trận phân quyền: `PreAuthorize("hasRole('INTERN')")` chuẩn mực.

### Bước 7: Viết Unit Tests & Gradle Compile Check
- **Tập tin:**
  * `src/test/java/org/example/internservice/intern/service/InternWeeklyReportServiceTest.java`
- Viết 6 test cases bao phủ 100% các kịch bản thành công và ngoại lệ (IDOR, tuần tương lai, đã có điểm đánh giá, gợi ý task Kanban, nộp báo cáo...).
- Chạy lệnh Gradle kiểm tra:
  ```powershell
  .\gradlew.bat :intern-and-program-service:test --tests "org.example.internservice.intern.service.InternWeeklyReportServiceTest"
  ```

---

## 4. Phân Tích Rủi Ro & Biện Pháp Giảm Thiểu (Risk Matrix)

| STT | Rủi ro tiềm ẩn | Mức độ | Biện pháp giảm thiểu |
| :---: | :--- | :---: | :--- |
| 1 | Lỗ hổng IDOR: TTS xem/sửa báo cáo của bạn khác | Cao | Trích xuất `userId` từ token xác thực của phiên làm việc, truy vấn `InternProfile` chính chủ, không nhận `internCode` từ request body của Intern. |
| 2 | Sửa báo cáo khi Mentor đã công bố điểm | Cao | Kiểm tra trạng thái `InternWeeklyAssessment.status == PUBLISHED` trước khi cho phép lưu hoặc nộp. Nếu đã publish, ném ngay `BadRequestException`. |
| 3 | Lỗi N+1 Query JPA khi tải danh sách nhiệm vụ | Trung bình | Sử dụng `JOIN FETCH` trong Repository khi tải chi tiết; tách biệt API timeline tóm tắt không load list tasks con. |
| 4 | Xung đột ghi đồng thời khi nộp báo cáo | Thấp | Khai báo Unique Constraint `(intern_code, week_number)` tại CSDL kết hợp `@Transactional`. |

---

## 5. Bảng Đối Chiếu 31 Nguyên Tắc Bất Biến Backend (Compliance Matrix)

| Nguyên Tắc | Nội Dung Ràng Buộc | Biện Pháp Đáp Ứng Trong TM-21 |
| :---: | :--- | :--- |
| **Rule 7** | Giữ ranh giới phân hệ (Boundary Isolation) | Tuyệt đối không can thiệp, không sửa bất kỳ file nào trong `InternHub-Frontend/`. |
| **Rule 14** | Tách DTO rõ ràng, không lộ Entity | Tạo riêng `SaveWeeklyReportRequest`, `WeeklyReportTimelineResponse`, `WeeklyReportDetailResponse`. |
| **Rule 16** | Tách Request và Response DTOs | Request và Response nằm ở 2 class riêng biệt. |
| **Rule 17** | Phòng chống God Class | Tách riêng `InternWeeklyReportService` & `InternWeeklyReportController`, không nhồi nhét vào service cũ. |
| **Rule 18** | Chuẩn hóa REST API & Swagger | Đầy đủ `@Tag`, `@Operation`, danh mục endpoint RESTful. |
| **Rule 19** | Chuẩn hóa `ApiResponse<T>` | Đóng gói 100% trong `ApiResponse<T>` với `code`, `success`, `message`, `data`. |
| **Rule 21** | Constructor Injection an toàn | Sử dụng `@RequiredArgsConstructor`, cấm `@Autowired` trên field. |
| **Rule 22** | Chống N+1 Query JPA | Sử dụng `JOIN FETCH` có chủ đích trong Repository. |
| **Rule 23** | Quản lý `@Transactional` | Khai báo `readOnly = true` ở class, `@Transactional` tường minh ở method ghi. |
| **Rule 25** | Dùng Lombok an toàn | Tuyệt đối không dùng `@Data`, chỉ dùng `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`. |
| **Rule 30** | Lưu trữ đặc tả vĩnh cửu | Đã lưu tại `docs/specs/TM-21-intern-weekly-report-spec.md` với đầy đủ Revision History. |
| **Rule 31** | Tái sử dụng tối đa mã nguồn | Khảo sát kỹ lưỡng và tái sử dụng `InternProfile`, `MissionItem` (TM-20), `InternWeeklyAssessment`, `WeeklyAssessmentService`. |
