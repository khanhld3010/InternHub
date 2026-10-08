# Walkthrough Tổng Hợp Triển Khai Backend TM-21: Thực Tập Sinh - Soạn Thảo & Nộp Báo Cáo Tuần

> **Mã Ticket Jira:** [TM-21](https://robluccibn9935.atlassian.net/browse/TM-21)  
> **Tiêu đề Jira:** *Intern - Soạn thảo và nộp báo cáo tuần (Weekly Report Creation & Submission)*  
> **Tài liệu đặc tả (Spec):** [TM-21-intern-weekly-report-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-21-intern-weekly-report-spec.md)  
> **Kế hoạch triển khai (Plan):** [TM-21-intern-weekly-report-plan.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-21-intern-weekly-report-plan.md)  
> **Trạng thái:** ACTIVE / IMPLEMENTED & VERIFIED  
> **Phân hệ phụ trách:** Backend (`intern-and-program-service`, `api-gateway`)  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc Backend, đặc biệt Rule 7, 14, 15, 16, 17, 18, 19, 21, 22, 23, 27, 30, 31).

---

## 1. Tuyên Ngôn Nghiệp Vụ & Ranh Giới Tính Năng (Boundary Scope)

1. **Phân định ranh giới TM-21 và TM-22:**
   - **TM-21 (100% Thực tập sinh):** Thực tập sinh quản lý tiến trình báo cáo cá nhân qua Timeline các tuần, nhận gợi ý nhiệm vụ tự động từ bảng Kanban ([TM-20](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md)), soạn thảo 4 trụ cột nghiệp vụ, lưu nháp (`DRAFT`), sửa đổi và nộp chính thức (`SUBMITTED`).
   - **TM-22 (100% Mentor):** Mentor tra cứu báo cáo của TTS được phân công phụ trách, thực hiện phản hồi nhận xét và chấm điểm định kỳ. Toàn bộ logic Mentor đã được tách rời độc lập sang ticket TM-22.

2. **4 Trụ Cột Nghiệp Vụ Cốt Lõi Của Báo Cáo Tuần:**
   - **Trụ cột 1 - Nhiệm vụ đã hoàn thành (`completedTasksSummary`):** Tự động liên kết các task Kanban `COMPLETED` của tuần.
   - **Trụ cột 2 - Nhiệm vụ chưa hoàn thành (`unfinishedTasksSummary`):** Tự động liên kết các task `IN_PROGRESS`/`TODO`, yêu cầu TTS giải trình nguyên nhân chậm tiến độ.
   - **Trụ cột 3 - Khó khăn, vướng mắc (`difficultiesAndChallenges`):** Nêu bật các rào cản kỹ thuật hoặc môi trường cần Mentor hỗ trợ gỡ rối.
   - **Trụ cột 4 - Kiến thức, bài học mới (`learningsAndKnowledge`):** Đúc kết giá trị chuyên môn đã thu nạp được sau tuần làm việc.

3. **Cơ Chế Bảo Vệ Dữ Liệu & Khóa Cứng (Immutability):**
   - TTS được phép cập nhật lại báo cáo sau khi nộp (`SUBMITTED`) **nếu và chỉ nếu** Mentor chưa công bố đánh giá (`PUBLISHED`).
   - Khi Mentor đã công bố điểm (`InternWeeklyAssessment.status == PUBLISHED`), trạng thái báo cáo chuyển thành `REVIEWED` và bị khóa cứng (throw `BadRequestException` nếu TTS cố sửa).

---

## 2. Kết Quả Triển Khai Chi Tiết (Backend Artifacts)

### 2.1. Thực Thể & Mô Hình Dữ Liệu (Entities - Rule 15, 31)
- [WeeklyReportStatus.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/enums/WeeklyReportStatus.java):
  - Enum gồm: `NOT_SUBMITTED`, `DRAFT`, `SUBMITTED`, `REVIEWED`.
- [InternWeeklyReport.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternWeeklyReport.java):
  - Entity ánh xạ bảng `intern_weekly_reports`.
  - Quản lý `internCode`, `weekNumber`, `startDate`, `endDate`, `status`, `submittedAt`, cùng 4 trường nội dung và `CascadeType.ALL` sang bảng nhiệm vụ chi tiết.
- [InternWeeklyReportTask.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternWeeklyReportTask.java):
  - Entity ánh xạ bảng `intern_weekly_report_tasks`.
  - Lưu snapshot trạng thái nhiệm vụ tại thời điểm báo cáo (`missionItemId`, `taskTitle`, `taskStatus`, `taskType`).

### 2.2. DTOs & Validation (Rule 16)
- [SaveWeeklyReportRequest.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/request/SaveWeeklyReportRequest.java): Request lưu nháp và nộp báo cáo kèm validation dữ liệu.
- [WeeklyReportTimelineResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/WeeklyReportTimelineResponse.java): DTO trả về danh sách các tuần trong kỳ thực tập, trạng thái báo cáo và điểm đánh giá nếu đã được Mentor publish.
- [WeeklyReportDetailResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/WeeklyReportDetailResponse.java): Chi tiết báo cáo tuần kèm danh sách tasks snapshot và phản hồi/điểm của Mentor.
- [SuggestedKanbanTasksResponse.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/SuggestedKanbanTasksResponse.java): Phân loại task Kanban trong tuần thành 2 nhóm: `completedTasks` và `unfinishedTasks`.

### 2.3. Repositories (Rule 22)
- [InternWeeklyReportRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternWeeklyReportRepository.java):
  - Sử dụng `LEFT JOIN FETCH r.tasks` trong `findByInternCodeAndWeekNumberWithTasks` để triệt tiêu lỗi N+1 Query.
- [InternWeeklyReportTaskRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternWeeklyReportTaskRepository.java): Thao tác với dữ liệu snapshot nhiệm vụ.

### 2.4. Service Layer (Rule 17, 21, 23)
- [InternWeeklyReportService.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/InternWeeklyReportService.java): Interface nghiệp vụ báo cáo tuần cho TTS.
- [InternWeeklyReportServiceImpl.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/impl/InternWeeklyReportServiceImpl.java):
  - Kiểm tra trạng thái kỳ thực tập, tính toán số tuần chính xác dựa vào `startDate` và `durationWeeks`.
  - Chặn nộp báo cáo cho các tuần trong tương lai chưa đến hạn.
  - Tích hợp gửi thông báo thời gian thực đến Mentor khi TTS bấm nộp chính thức (`submitWeeklyReport`).
  - Đảm bảo an toàn phân quyền: Lấy `internCode` từ `internProfileRepository.findByUserId(userId)` (chống IDOR).

### 2.5. Controller Layer (Rule 18, 19)
- [InternWeeklyReportController.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/controller/InternWeeklyReportController.java):
  - `@RestController` tại `/api/interns/my-weekly-reports` và `@PreAuthorize("hasRole('INTERN')")`.
  - Cung cấp 6 RESTful endpoints:
    1. `GET /api/interns/my-weekly-reports/timeline`: Lấy danh sách timeline các tuần.
    2. `GET /api/interns/my-weekly-reports/suggested-tasks`: Lấy gợi ý nhiệm vụ từ Kanban TM-20.
    3. `GET /api/interns/my-weekly-reports/{weekNumber}`: Xem chi tiết báo cáo theo tuần.
    4. `POST /api/interns/my-weekly-reports/{weekNumber}/draft`: Lưu nháp báo cáo tuần.
    5. `POST /api/interns/my-weekly-reports/{weekNumber}/submit`: Nộp chính thức báo cáo tuần.
    6. `PUT /api/interns/my-weekly-reports/{weekNumber}`: Cập nhật nội dung báo cáo tuần.

---

## 3. Kết Quả Kiểm Thử Độc Lập (Automated Unit Testing - Rule 27)

Toàn bộ 7 unit test cases trong [InternWeeklyReportServiceTest.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/test/java/org/example/internservice/intern/service/InternWeeklyReportServiceTest.java) đã được thực thi và đạt **100% PASS**:

```bash
.\gradlew.bat :intern-and-program-service:test --tests "org.example.internservice.intern.service.InternWeeklyReportServiceTest" --rerun-tasks --info
```

| STT | Tên Test Case | Mục Đích & Kiểm Tra Nghiệp Vụ | Kết Quả |
| :---: | :--- | :--- | :---: |
| 1 | `UT-01: Lấy timeline các tuần` | Lấy danh sách tuần của TTS thành công, hiển thị chính xác trạng thái báo cáo và điểm số | ✅ PASS |
| 2 | `UT-02: Gợi ý nhiệm vụ Kanban` | Phân loại chính xác 2 nhóm `COMPLETED` và `UNFINISHED` từ Mission Items TM-20 | ✅ PASS |
| 3 | `UT-03: Lưu nháp báo cáo tuần` | Lưu bản nháp `DRAFT` thành công, không dispatch thông báo gửi Mentor | ✅ PASS |
| 4 | `UT-04: Nộp chính thức báo cáo tuần` | Chuyển trạng thái sang `SUBMITTED`, tự động dispatch thông báo thời gian thực đến Mentor | ✅ PASS |
| 5 | `UT-05: Chặn sửa khi Mentor đã publish` | Ném `BadRequestException` khi sửa báo cáo mà Mentor đã công bố điểm (`PUBLISHED`) | ✅ PASS |
| 6 | `UT-06: Chặn tuần tương lai` | Ném `BadRequestException` khi TTS cố tình nộp báo cáo cho tuần chưa tới hạn | ✅ PASS |
| 7 | `UT-07: Xem chi tiết báo cáo tuần` | Đọc toàn bộ chi tiết báo cáo tuần, bao gồm danh sách tasks snapshot và phản hồi | ✅ PASS |

**Tổng kết Build:** `BUILD SUCCESSFUL in 29s`, 5 executed tasks, 0 failures, 0 regressions.

---

## 4. Cam Kết & Giữ Vững Ranh Giới (Boundary Isolation)
- **Frontend Isolation (Rule 7):** Tuyệt đối không can thiệp, không chỉnh sửa bất kỳ file mã nguồn React/TypeScript nào tại thư mục `InternHub-Frontend/`.
- **Database Safety (Rule 8):** Cấu hình JPA DDL tự động sinh bảng mà không gây phá hoại bất kỳ bảng dữ liệu nào đang có.
- **Git Safety (Rule 4):** Giữ nguyên các thay đổi trong working tree, không tự ý commit/push để lập trình viên chủ động review.
