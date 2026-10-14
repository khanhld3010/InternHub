# Báo Cáo Triển Khai Kỹ Thuật (Technical Implementation Walkthrough)
## TM-22: Mentor - Xem Báo Cáo Tuần & Đánh Giá Phản Hồi (Mentor Weekly Report Review & Assessment Workspace)

> **Mã Jira Ticket:** [TM-22](https://robluccibn9935.atlassian.net/browse/TM-22)  
> **Tiêu đề Jira:** *Mentor - Xem Báo Cáo Tuần & Đánh Giá Phản Hồi (Mentor Weekly Report Review & Assessment Workspace)*  
> **Tài liệu đặc tả (Spec):** [TM-22-mentor-review-weekly-report-spec.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-22-mentor-review-weekly-report-spec.md)  
> **Kế hoạch triển khai:** [TM-22-mentor-review-weekly-report-plan.md](file:///d:/codegym_final_project/InternHub/docs/specs/TM-22-mentor-review-weekly-report-plan.md)  
> **Trạng thái:** **HOÀN TẤT THỰC THI (IMPLEMENTATION COMPLETED & VERIFIED)**  
> **Service phụ trách:** `intern-and-program-service` (Port 8082), `api-gateway` (Port 8080)  
> **Tuân thủ quy chuẩn:** Tuân thủ 100% [AGENTS.md](file:///d:/codegym_final_project/InternHub/AGENTS.md) (31 nguyên tắc bất biến của Backend, bao gồm Rule 7 Boundary Isolation, Rule 14, 16, 17, 18, 19, 21, 22, 23, 27, 30, 31).

---

## 1. Tóm Tắt Kết Quả Triển Khai (Executive Summary)

Phân hệ Backend cho ticket **TM-22** đã được hoàn thành 100% theo đúng tài liệu đặc tả kỹ thuật và kế hoạch đã được phê duyệt. Tính năng này giải quyết triệt để vấn đề "đánh giá mù" của Mentor, mang lại không gian đối soát song song giữa Báo cáo tuần 4 trụ cột của Thực tập sinh (từ TM-21) và Bàn đánh giá 4 tiêu chí chuẩn hóa của Mentor.

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        CÁC TÍNH NĂNG ĐÃ TRIỂN KHAI CHO TM-22                           │
├────────────────────────────────┬───────────────────────────────────────────────────────┤
│ 1. ĐỐI SOÁT DUAL-PANE          │ GET /api/mentors/my-interns/{code}/weekly-reports/{w} │
│                                │ Trả về song song Báo cáo TTS + Snapshot Tasks + Form  │
│                                │ đánh giá của Mentor. Kiểm tra IDOR nghiêm ngặt.       │
├────────────────────────────────┼───────────────────────────────────────────────────────┤
│ 2. YÊU CẦU LÀM LẠI BÁO CÁO     │ POST .../weekly-reports/{weekNumber}/request-revision │
│    (Request Revision)          │ Chuyển trạng thái sang REVISION_REQUESTED, lưu ghi    │
│                                │ chú revisionNote và bắn thông báo real-time cho TTS.  │
├────────────────────────────────┼───────────────────────────────────────────────────────┤
│ 3. ĐỒNG BỘ KHÓA BÁO CÁO        │ Khi Mentor lưu với isPublish = true: tự động chuyển   │
│    (Auto-Sync Status)          │ InternWeeklyReport.status sang REVIEWED (khóa sửa).   │
├────────────────────────────────┼───────────────────────────────────────────────────────┤
│ 4. LINH HOẠT & CẬP NHẬT        │ Mentor có thể chấm điểm dù TTS chưa nộp báo cáo, và   │
│    (Flexibility & Update)      │ có thể cập nhật lại đánh giá trong thời gian kỳ thực  │
│                                │ tập đang diễn ra.                                     │
└────────────────────────────────┴───────────────────────────────────────────────────────┘
```

---

## 2. Danh Mục Tệp Tin Mã Nguồn Đã Thực Thi (Source Code Manifest)

### 2.1. Tệp tin tạo mới (New Files)
1. [`RequestReportRevisionRequest.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/request/RequestReportRevisionRequest.java): DTO nhận lý do yêu cầu chỉnh sửa (`revisionNote`) có validation `@NotBlank`.
2. [`MentorWeeklyReportReviewResponse.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/MentorWeeklyReportReviewResponse.java): DTO trả về dữ liệu đối soát Dual-Pane cho Mentor (lồng cấu trúc `WeeklyReportDetailResponse` và `WeeklyAssessmentResponse`).
3. [`ReportRevisionResponse.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/ReportRevisionResponse.java): DTO phản hồi kết quả sau khi chuyển báo cáo sang `REVISION_REQUESTED`.
4. [`WeeklyAssessmentServiceTest.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/test/java/org/example/internservice/intern/service/WeeklyAssessmentServiceTest.java): Bộ test tự động gồm 7 test cases bao phủ toàn diện các kịch bản AC-1 đến AC-8.

### 2.2. Tệp tin cập nhật (Updated Files)
1. [`WeeklyReportStatus.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/enums/WeeklyReportStatus.java): Bổ sung giá trị `REVISION_REQUESTED`.
2. [`InternWeeklyReport.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternWeeklyReport.java): Thêm trường `@Column(name = "revision_note") private String revisionNote;` và cập nhật độ dài `status`.
3. [`WeeklyReportDetailResponse.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/WeeklyReportDetailResponse.java): Thêm trường `revisionNote` để TTS đọc được lý do Mentor yêu cầu sửa.
4. [`InternWeeklyReportServiceImpl.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/impl/InternWeeklyReportServiceImpl.java): Cập nhật hiển thị timeline và mapper chi tiết hỗ trợ `REVISION_REQUESTED` và `revisionNote`.
5. [`WeeklyAssessmentService.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/WeeklyAssessmentService.java): Khai báo 2 method mới cho phân hệ Mentor.
6. [`WeeklyAssessmentServiceImpl.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/impl/WeeklyAssessmentServiceImpl.java): Triển khai kiểm tra IDOR an toàn, nạp chi tiết báo cáo kèm snapshot tasks (`JOIN FETCH`), chuyển trạng thái `REVISION_REQUESTED`, đồng bộ khóa `REVIEWED` khi publish và bắn thông báo thời gian thực.
7. [`WeeklyAssessmentController.java`](file:///d:/codegym_final_project/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/controller/WeeklyAssessmentController.java): Bổ sung 2 endpoints RESTful cho Mentor với `@PreAuthorize`.

---

## 3. Kết Quả Kiểm Thử Tự Động (Verification & Test Results)

### 3.1. Biên dịch Java sạch sẽ 100%
```text
> Task :intern-and-program-service:compileJava
BUILD SUCCESSFUL in 19s
1 actionable task: 1 executed
```

### 3.2. Chạy bộ Unit Test TM-22 (`WeeklyAssessmentServiceTest`)
```text
> Task :intern-and-program-service:test
BUILD SUCCESSFUL in 20s
- UT-BE-01: Mentor lấy chi tiết báo cáo đối soát của TTS phụ trách thành công (Dual-Pane)  --> PASSED
- UT-BE-02: Ném AccessDeniedException khi Mentor khác truy cập TTS (chống IDOR)           --> PASSED
- UT-BE-03: Mentor yêu cầu sửa lại báo cáo thành công, đổi REVISION_REQUESTED & bắn notif   --> PASSED
- UT-BE-04: Ném BadRequestException khi yêu cầu sửa báo cáo chưa được nộp                   --> PASSED
- UT-BE-05: Khi isPublish = true, assessment PUBLISHED và report đồng bộ REVIEWED           --> PASSED
- UT-BE-06: Mentor chấm điểm thành công cho tuần mà TTS chưa nộp báo cáo                    --> PASSED
- UT-BE-07: Mentor cập nhật lại đánh giá đã publish thành công, tính lại điểm TB chuẩn      --> PASSED
```

### 3.3. Kiểm thử hồi quy TM-21 (`InternWeeklyReportServiceTest`)
```text
> Task :intern-and-program-service:test
BUILD SUCCESSFUL in 14s (6/6 tests PASSED - Zero Regressions)
```

---

## 4. Hướng Dẫn Tích Hợp Cho Phân Hệ Frontend (Frontend Integration Guide)

Khi triển khai giao diện Frontend cho TM-22 trên `InternHub-Frontend`, các kỹ sư Frontend sẽ tích hợp với 2 endpoints mới như sau:

1. **Gọi lấy dữ liệu đối soát Dual-Pane:**
   - URL: `GET /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}`
   - Header: `Authorization: Bearer <TOKEN>` (Role `MENTOR`, `HR`, `ADMIN`)
   - Dữ liệu nhận được: Cột trái hiển thị `res.data.report` (4 trụ cột, snapshot tasks); Cột phải nạp vào form đánh giá `res.data.assessment`.

2. **Gọi yêu cầu làm lại báo cáo:**
   - URL: `POST /api/mentors/my-interns/{internCode}/weekly-reports/{weekNumber}/request-revision`
   - Body: `{"revisionNote": "Lý do yêu cầu làm lại..."}`
   - Sau khi gọi thành công, toast thông báo và cập nhật badge trên UI thành "Yêu cầu chỉnh sửa lại".

---

## 5. Kết Luận
Tính năng Backend cho ticket **TM-22** đã được hoàn tất trọn vẹn, tuân thủ tuyệt đối các nguyên tắc bất biến của hệ thống InternHub, bảo đảm an toàn dữ liệu, chống lỗi N+1 Query, chống IDOR và sẵn sàng phục vụ phân hệ Frontend.
