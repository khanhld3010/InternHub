# Kế Hoạch Triển Khai Kỹ Thuật (Technical Implementation Plan) - TM-22
## Mentor - Xem Báo Cáo Tuần & Đánh Giá Phản Hồi (Mentor Weekly Report Review & Assessment Workspace)

> **Mã Jira Ticket:** [TM-22](https://robluccibn9935.atlassian.net/browse/TM-22)  
> **Tiêu đề Jira:** *Mentor - Xem Báo Cáo Tuần & Đánh Giá Phản Hồi (Mentor Weekly Report Review & Assessment Workspace)*  
> **Tài liệu đặc tả (Spec):** [TM-22-mentor-review-weekly-report-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-22-mentor-review-weekly-report-spec.md)  
> **Kế hoạch khảo sát đã duyệt:** [TM-22-pre-spec-discovery-and-action-plan.md](file:///C:/Users/admin/.gemini/antigravity-ide/brain/8646417a-8040-456e-a314-c050b7bac2bd/TM-22-pre-spec-discovery-and-action-plan.md)  
> **Nối tiếp trực tiếp:** [TM-19](file:///d:/codegym_final_project/InternHub/docs/specs/TM-19-mentor-assign-tasks-spec.md), [TM-20](file:///d:/codegym_final_project/InternHub/docs/specs/TM-20-intern-update-task-progress-spec.md) & [TM-21](file:///d:/codegym_final_project/InternHub/docs/specs/TM-21-intern-weekly-report-spec.md)  
> **Trạng thái:** PROPOSED / CHỜ PHÊ DUYỆT (PENDING APPROVAL)  
> **Cấp độ thay đổi (Change Level):** **L3** (Mở rộng Enum & CSDL, thêm 2 REST API endpoints cho Mentor, nâng cấp Service đồng bộ trạng thái 2 chiều, kiểm soát IDOR an toàn và tích hợp dispatch thông báo real-time).  
> **Microservices phụ trách:** `intern-and-program-service` (Port 8082), `api-gateway` (Port 8080).  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% tài liệu [`.agents/`](file:///d:/codegym_final_project/InternHub/.agents/) và [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc bất biến của Backend).

---

## 1. Khảo Sát Hiện Trạng & Đánh Giá Tái Sử Dụng Mã Nguồn (MANDATORY DIRECTIVE - Rule 31)

> [!CAUTION]
> **CHỈ THỊ CỐT LÕI (RULE 31): "ĐẢM BẢO SẼ QUÉT DỰ ÁN, TRÁNH VIỆC TẠO THÊM CODE MỚI KHÔNG CẦN THIẾT, SỬ DỤNG TỐI ĐA NHỮNG GÌ ĐÃ CÓ ĐỂ PHÁT TRIỂN"**

### 1.1. Rà soát hiện trạng mã nguồn & Database Schema
1. **Các thực thể & bảng CSDL hiện hữu:**
   - [InternWeeklyAssessment.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternWeeklyAssessment.java) (`intern_weekly_assessments`): Đã có các cột điểm rubrics: `technical_score`, `attitude_score`, `teamwork_score`, `productivity_score`, `average_score`, `feedback`, `next_week_goals`, `status` (`DRAFT`, `PUBLISHED`), `published_at`.
   - [InternWeeklyAssessmentRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternWeeklyAssessmentRepository.java): Đã có các query tra cứu theo `internCode` và `weekNumber`.
   - [InternWeeklyReport.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternWeeklyReport.java) (`intern_weekly_reports`): Chứa 4 trụ cột báo cáo của TTS và danh sách snapshot tasks từ TM-21.
   - [InternWeeklyReportRepository.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternWeeklyReportRepository.java): Đã có sẵn query `findByInternCodeAndWeekNumberWithTasks` (đã tối ưu `JOIN FETCH r.tasks` chống N+1 query).
   - [InternProfile.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternProfile.java): Chứa `mentorId`, `startDate`, `endDate`, `status` dùng để kiểm tra phân công Mentor chống IDOR.
   - [NotificationEventDispatcher.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/client/NotificationEventDispatcher.java): Cơ chế bắn thông báo thời gian thực nội bộ.

### 1.2. Đánh giá tái sử dụng & Quyết định thiết kế
- **Tái sử dụng 100% (Zero Redundancy):**
  - Tái sử dụng bảng `intern_weekly_assessments` và entity `InternWeeklyAssessment`: Không tạo thêm bảng đánh giá mới.
  - Tái sử dụng query `findByInternCodeAndWeekNumberWithTasks` của `InternWeeklyReportRepository` để nạp báo cáo và snapshot tasks chỉ với 1 query duy nhất.
  - Tái sử dụng `NotificationEventDispatcher` để bắn sự kiện `WEEKLY_ASSESSMENT_PUBLISHED` và `REPORT_REVISION_REQUESTED`.
  - Tái sử dụng `WeeklyReportDetailResponse` bên trong DTO đối soát của Mentor.
- **Mở rộng có kiểm soát:**
  - Bổ sung `REVISION_REQUESTED` vào enum [WeeklyReportStatus.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/enums/WeeklyReportStatus.java).
  - Bổ sung trường `revision_note` trên [InternWeeklyReport.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternWeeklyReport.java) và `WeeklyReportDetailResponse.java`.
  - Mở rộng [WeeklyAssessmentService.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/WeeklyAssessmentService.java) và `WeeklyAssessmentServiceImpl.java`: Bổ sung API xem đối soát và API yêu cầu làm lại; nâng cấp hàm `saveAssessment` tự động đồng bộ `InternWeeklyReport.status = REVIEWED` khi publish.
  - Mở rộng [WeeklyAssessmentController.java](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/controller/WeeklyAssessmentController.java): Bổ sung 2 endpoints RESTful cho Mentor.
- **Bảo vệ an toàn dữ liệu & IDOR:**
  - Mentor chỉ xem và đánh giá được sinh viên do mình trực tiếp phụ trách (`internProfile.mentorId == currentMentorId`). `ROLE_HR` và `ROLE_ADMIN` được phép truy cập toàn quyền.

---

## 2. Mục Tiêu Kỹ Thuật & Tuyên Ngôn Nghiệp Vụ

> **ĐỊNH NGHĨA CỐT LÕI: "ĐÁNH GIÁ VÀ PHẢN HỒI BÁO CÁO TUẦN LÀ HOẠT ĐỘNG KHÉP KÍN VÒNG LẶP HỌC TẬP (FEEDBACK LOOP). NƠI MENTOR ĐỐI CHIẾU TRỰC TIẾP GIỮA BÁO CÁO 4 TRỤ CỘT CỦA THỰC TẬP SINH VỚI KẾT QUẢ KANBAN THỰC TẾ, CHẤM ĐIỂM 4 TIÊU CHÍ RUBRICS CHUẨN HÓA, GỬI ĐỊNH HƯỚNG CHUYÊN MÔN KỊP THỜI HOẶC YÊU CẦU LÀM LẠI NẾU THIẾU MINH CHỨNG."**

1. **Tra cứu đối soát song song Dual-Pane:** API `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}` cung cấp toàn bộ báo cáo, snapshot tasks và đánh giá của tuần.
2. **Yêu cầu làm lại báo cáo:** API `POST /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}/request-revision` chuyển trạng thái sang `REVISION_REQUESTED`, lưu ghi chú lý do và bắn thông báo cho TTS.
3. **Đồng bộ khóa báo cáo 2 chiều:** Khi Mentor công bố (`PUBLISHED`) đánh giá tại `POST /api/interns/{internCode}/weekly-assessments`, tự động chuyển `InternWeeklyReport.status` sang `REVIEWED`, khóa cứng với TTS.
4. **Linh hoạt tiến trình:** Cho phép Mentor chủ động chấm điểm ngay cả khi TTS chưa nộp báo cáo (`NOT_STARTED` / `DRAFT`) để tránh gián đoạn tiến độ chung.
5. **Cập nhật trong kỳ:** Cho phép Mentor cập nhật lại đánh giá tuần đã công bố khi kỳ thực tập còn diễn ra.

---

## 3. Kiến Trúc Kỹ Thuật & Danh Mục Files Thay Đổi

### 3.1. Các tệp tin chỉnh sửa (Modifications)
| Tệp tin | Vị trí | Mục đích thay đổi |
| :--- | :--- | :--- |
| `WeeklyReportStatus.java` | `intern/entity/enums/` | Bổ sung giá trị `REVISION_REQUESTED` |
| `InternWeeklyReport.java` | `intern/entity/` | Thêm trường `@Column(name = "revision_note") private String revisionNote;` |
| `WeeklyReportDetailResponse.java` | `intern/dto/response/` | Thêm trường `revisionNote` để TTS xem được lý do Mentor yêu cầu sửa |
| `WeeklyAssessmentService.java` | `intern/service/` | Khai báo method `getWeeklyReportForMentor` và `requestReportRevision` |
| `WeeklyAssessmentServiceImpl.java` | `intern/service/impl/` | Triển khai logic xem đối soát, kiểm tra IDOR, yêu cầu sửa đổi, đồng bộ khóa `REVIEWED` khi publish |
| `WeeklyAssessmentController.java` | `intern/controller/` | Bổ sung 2 endpoints: `GET .../weekly-reports/{weekNumber}` và `POST .../request-revision` |

### 3.2. Các tệp tin tạo mới (New Files)
| Tệp tin | Vị trí | Mục đích |
| :--- | :--- | :--- |
| `RequestReportRevisionRequest.java` | `intern/dto/request/` | DTO nhận lý do yêu cầu sửa đổi (`revisionNote`) từ Mentor |
| `MentorWeeklyReportReviewResponse.java` | `intern/dto/response/` | DTO trả về dữ liệu đối soát Dual-Pane cho Mentor |
| `ReportRevisionResponse.java` | `intern/dto/response/` | DTO trả về kết quả yêu cầu sửa đổi báo cáo |
| `WeeklyAssessmentServiceTest.java` | `src/test/java/.../service/` | Unit test toàn diện bao phủ các kịch bản AC-1 đến AC-8 |

---

## 4. Kế Hoạch 6 Bước Triển Khai Chi Tiết (Step-by-Step Technical Workflow)

```text
[Bước 1: Enum & Entity] ➔ [Bước 2: Xây dựng DTOs] ➔ [Bước 3: Service Layer]
         ↓
[Bước 4: Controller Layer] ➔ [Bước 5: Unit Tests] ➔ [Bước 6: Gradle Compile & Test]
```

### Bước 1: Cập nhật Entity & Enum
- Thêm `REVISION_REQUESTED` vào `WeeklyReportStatus.java`.
- Thêm trường `revisionNote` vào `InternWeeklyReport.java`.
- Thêm trường `revisionNote` vào `WeeklyReportDetailResponse.java`.

### Bước 2: Xây dựng các DTOs cho TM-22
- Tạo `RequestReportRevisionRequest.java` với validation `@NotBlank` trên `revisionNote`.
- Tạo `MentorWeeklyReportReviewResponse.java` chứa thông tin TTS, tuần, `WeeklyReportDetailResponse report`, và `WeeklyAssessmentResponse assessment`.
- Tạo `ReportRevisionResponse.java` trả về thông tin trạng thái sau khi yêu cầu sửa đổi.

### Bước 3: Nâng cấp Service Layer (`WeeklyAssessmentServiceImpl`)
- Inject `InternWeeklyReportRepository reportRepository`.
- Viết private helper kiểm tra IDOR:
  ```java
  private void validateMentorAccess(InternProfile intern, Long mentorId, String role) {
      if ("ROLE_HR".equals(role) || "ROLE_ADMIN".equals(role) || "HR".equals(role) || "ADMIN".equals(role)) {
          return;
      }
      if (intern.getMentorId() == null || !intern.getMentorId().equals(mentorId)) {
          throw new AccessDeniedException("Bạn không có quyền truy cập hoặc đánh giá thực tập sinh này.");
      }
  }
  ```
- Triển khai `getWeeklyReportForMentor`: nạp TTS, validate IDOR, nạp report qua `findByInternCodeAndWeekNumberWithTasks`, nạp assessment, map sang `MentorWeeklyReportReviewResponse`.
- Triển khai `requestReportRevision`: nạp TTS, validate IDOR, nạp report (phải đang `SUBMITTED`), đổi status sang `REVISION_REQUESTED`, lưu `revisionNote`, bắn notification `REPORT_REVISION_REQUESTED`.
- Nâng cấp `saveAssessment`: thêm kiểm tra IDOR; khi `isPublish == true`, tìm report và đổi `status = WeeklyReportStatus.REVIEWED`.

### Bước 4: Mở rộng Controller Layer (`WeeklyAssessmentController`)
- Thêm endpoint `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}` với `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`.
- Thêm endpoint `POST /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}/request-revision` với `@PreAuthorize("hasAnyRole('MENTOR', 'HR', 'ADMIN')")`.
- Trích xuất `mentorId`, `mentorName`, `role` từ `Authentication`.

### Bước 5: Viết Unit Tests (`WeeklyAssessmentServiceTest`)
- Viết 7 test cases bao phủ:
  1. `getWeeklyReportForMentor_Success`
  2. `getWeeklyReportForMentor_Fail_IDOR`
  3. `requestRevision_Success`
  4. `requestRevision_Fail_NotSubmitted`
  5. `saveAssessment_Publish_SyncReportStatus`
  6. `saveAssessment_Success_WithoutReport`
  7. `saveAssessment_UpdatePublishedAssessment`

### Bước 6: Kiểm tra biên dịch Gradle & Chạy Tests
- Chạy `.\gradlew :intern-and-program-service:compileJava`
- Chạy `.\gradlew :intern-and-program-service:test --tests "org.example.internservice.intern.service.WeeklyAssessmentServiceTest"`
- Đảm bảo 100% tests pass và không có lỗi biên dịch.

---

## 5. Tiêu Chí Nghiệm Thu (Acceptance Criteria & Verification Plan)

- [ ] **AC-1:** Mentor gọi `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}` lấy đầy đủ dữ liệu đối soát Dual-Pane.
- [ ] **AC-2:** Chặn IDOR: Mentor khác gọi báo lỗi 403 Forbidden. HR/Admin truy cập bình thường.
- [ ] **AC-3:** Mentor bấm yêu cầu sửa đổi: Báo cáo chuyển sang `REVISION_REQUESTED`, TTS nhận notification.
- [ ] **AC-4:** Chấm điểm 4 tiêu chí rubrics (1-5 sao) tính điểm TB chính xác làm tròn 1 số thập phân.
- [ ] **AC-5:** Khi publish đánh giá, `InternWeeklyReport` tự động chuyển sang `REVIEWED`, khóa cứng với TTS.
- [ ] **AC-6:** Mentor chấm điểm được ngay cả khi TTS chưa nộp báo cáo tuần.
- [ ] **AC-7:** Mentor cập nhật lại được đánh giá đã publish trong thời gian kỳ thực tập còn diễn ra.
- [ ] **AC-8:** Bộ test tự động pass 100% không cảnh báo.
