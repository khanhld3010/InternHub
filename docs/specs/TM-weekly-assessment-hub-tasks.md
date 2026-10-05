# Kế hoạch triển khai: Mentor Dashboard Scale-Up & Mentorship Evaluation Hub

**Spec liên kết:** [`docs/specs/TM-weekly-assessment-hub-spec.md`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/TM-weekly-assessment-hub-spec.md)  
**Nhánh Git:** `feature/mentor-evaluation-hub`

---

## Task Checklist

### Phase 1: Frontend Components & Mock Data Integration (View Layer)
- [ ] **Task 1.1:** Tạo Types cho Weekly Assessment & Mentor Triage Overview (`types/assessment.ts` hoặc cập nhật `types/index.ts`).
- [ ] **Task 1.2:** Xây dựng component `MentorTriageHeader.tsx` (KPIs: Tổng phụ trách, Cần đánh giá tuần này có border glow, Quá hạn Midterm, Điểm TB).
- [ ] **Task 1.3:** Xây dựng component `MentorFilterBar.tsx` (Search input, Filter pills: Tất cả / Cần chú ý / Ổn định, Program dropdown, View mode toggle Grid / Table).
- [ ] **Task 1.4:** Xây dựng component `MentorInternBentoCard.tsx` (Hiển thị thẻ TTS theo đúng Mockup scale: avatar, tên, tiến độ tuần % bar, badge trạng thái, điểm số).
- [ ] **Task 1.5:** Xây dựng component `MentorWeeklyEvaluationHub.tsx` (4 tiêu chí sao 1-5 ⭐, smart templates tĩnh, nút AI Coming Soon, textfields feedback & nextWeekGoals, nút Draft/Publish).
- [ ] **Task 1.6:** Xây dựng `MentorInternDetailDrawer.tsx` (Slide-over drawer toàn màn hình thay thế layout 2 cột cũ, tích hợp Evaluation Hub).
- [x] **Task 1.7:** Tái cấu trúc trang chính `MentorDashboard.tsx` ghép nối các component trên, hỗ trợ cả 2 view mode (Bento Card Grid & Data Table List).

### Phase 2: Backend Entities, Repository & Service (Data Layer)
- [x] **Task 2.1:** Tạo Entity JPA `InternWeeklyAssessment.java` trong `intern-and-program-service`.
- [x] **Task 2.2:** Tạo Spring Data JPA Repository `InternWeeklyAssessmentRepository.java`.
- [x] **Task 2.3:** Tạo DTO Request/Response (`WeeklyAssessmentRequest.java`, `WeeklyAssessmentResponse.java`, `MentorTriageOverviewResponse.java`).
- [x] **Task 2.4:** Viết Business Service `WeeklyAssessmentService.java` xử lý tính điểm TB, kiểm tra quyền publish, lọc draft theo role.
- [x] **Task 2.5:** Viết REST Controller `WeeklyAssessmentController.java` & cấu hình bảo mật gateway.

### Phase 3: Tích hợp API End-to-End & Kiểm thử (Verification)
- [x] **Task 3.1:** Viết `assessmentService.ts` phía Frontend gọi API backend.
- [x] **Task 3.2:** Test luồng Mentor tạo đánh giá tuần (Draft -> Publish).
- [ ] **Task 3.3:** Test luồng Thực tập sinh xem đánh giá tuần trên Intern Dashboard.
- [x] **Task 3.4:** Build check kiểm tra lỗi typescript / linting.
