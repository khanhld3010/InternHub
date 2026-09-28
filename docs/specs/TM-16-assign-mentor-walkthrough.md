# Walkthrough Tổng Hợp Triển Khai TM-16: Phân Công Thực Tập Sinh Cho Mentor

## 1. Tổng Quan Nhiệm Vụ
- **Mã Ticket Jira**: `TM-16` (*Phân công thực tập sinh cho mentor*)
- **Trạng thái Ticket**: `In Progress`
- **Nhánh phát triển**: `feature/TM-16/assign-mentor-to-intern` (đồng bộ trên cả 2 repository `InternHub` và `InternHub-Frontend`).
- **Phạm vi hoàn tất**:
  1. Chuẩn hóa 3 trạng thái phân công: `ACTIVE`, `REPLACED`, `REVOKED` (cấm tuyệt đối `INACTIVE`).
  2. Chuẩn hóa 7 trạng thái thực tập sinh theo Design System: `PENDING`, `APPROVED`, `INTERNING`, `ON_HOLD`, `COMPLETED`, `REJECTED`, `TERMINATED`.
  3. Cài đặt cơ chế điều kiện kép: TTS chuyển `INTERNING` khi và chỉ khi `(mentorId != null) AND (program.status == 'ONGOING')`.
  4. Quy tắc thứ tự ưu tiên Program ➔ Mentor: Chặn gán mentor nếu `needsReassignment == true`.
  5. Cơ chế Thu hồi & Thay thế: Yêu cầu `replaceReason` khi thay thế mentor, cho phép thu hồi mentor trắng qua `DELETE /api/interns/{id}/mentor` và tự động gắn cờ `needsMentorReassignment = true`.

---

## 2. Kết Quả Triển Khai Chi Tiết

### 2.1. Backend (`InternHub`)
- **Enums & Entities**:
  - [`MentorAssignmentStatus.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/enums/MentorAssignmentStatus.java): Khai báo 3 trạng thái `ACTIVE`, `REPLACED`, `REVOKED`.
  - [`InternProfile.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternProfile.java): Bổ sung `mentorId`, `mentorName`, `mentorEmail`, `needsMentorReassignment`, `mentorReassignmentReason`.
  - [`InternMentorAssignment.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternMentorAssignment.java): Lưu vết lịch sử phân công, người phân công, ngày thu hồi và lý do.
- **Service & Repositories**:
  - [`InternMentorAssignmentRepository.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternMentorAssignmentRepository.java)
  - [`IdentityServiceClient.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/client/IdentityServiceClient.java): Kết nối qua REST để truy vấn danh sách Mentor từ service Identity.
  - [`InternProfileServiceImpl.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/impl/InternProfileServiceImpl.java): Xử lý `assignMentor`, `revokeMentor`, `getMentorHistory`, `getAvailableMentors`.
  - [`ProgramLifecycleJob.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/job/ProgramLifecycleJob.java): Tự động chuyển TTS sang `INTERNING` khi chương trình chuyển sang `ONGOING`.
- **REST Endpoints**:
  - `POST /api/interns/{id}/assign-mentor`: Phân công hoặc thay thế mentor.
  - `DELETE /api/interns/{id}/mentor`: Thu hồi mentor (gỡ trắng).
  - `GET /api/interns/{id}/mentor-history`: Lấy lịch sử phân công mentor của TTS.
  - `GET /api/interns/mentors`: Lấy danh sách Mentor khả dụng kèm số lượng TTS đang phụ trách.
- **Unit Tests**:
  - [`InternProfileMentorTest.java`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/test/java/org/example/internservice/intern/service/InternProfileMentorTest.java): Bao phủ toàn bộ các kịch bản gán mới, thay thế có lý do, thu hồi gỡ trắng, chặn khi chưa có chương trình, và kích hoạt điều kiện kép.

---

### 2.2. Frontend (`InternHub-Frontend`)
- **Design System & Styles**:
  - [`index.css`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/index.css): Cập nhật màu sắc badge chuẩn WCAG: `--sky` cho `APPROVED`, `--slate` cho `REJECTED`, `--neutral` cho `ON_HOLD`, `--danger` cho `TERMINATED`. Không blink animation, không dùng `--primary` cho badge.
- **Types & Services**:
  - [`intern.types.ts`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/types/intern.types.ts): Khai báo 7 trạng thái Intern, `MentorOption`, `AssignMentorRequest`, `RevokeMentorRequest`, `MentorAssignmentResponse`.
  - [`intern.endpoints.ts`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/constants/endpoints/intern.endpoints.ts) & [`internService.ts`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/services/internService.ts): Tích hợp đầy đủ 4 API endpoint phân công mentor.
- **UI Components**:
  - [`AssignMentorModal.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/AssignMentorModal.tsx): Modal chọn mentor (kèm số lượng học viên đang phụ trách), tự động chuyển đổi sang giao diện "Đổi Mentor" khi phát hiện TTS đã có mentor đương nhiệm (bắt buộc nhập lý do thay thế).
  - [`RevokeMentorModal.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/RevokeMentorModal.tsx): Modal thu hồi mentor với xác nhận cảnh báo và nhập lý do.
  - [`HrInternTable.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/HrInternTable.tsx): Thêm cột "Mentor phụ trách" hiển thị tên mentor, badge tĩnh cảnh báo "Chưa có Mentor" (cam), "Cần đổi Mentor" (đỏ) và các nút hành động nhanh: "Gán Mentor", "Đổi", "Thu hồi".
  - [`DetailInternModal.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/DetailInternModal.tsx): Bổ sung thông tin chi tiết Mentor đương nhiệm, Chương trình tham gia và cảnh báo điều phối.
  - [`HrDashboard.tsx`](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/HrDashboard.tsx): Kết nối toàn diện luồng phân công và thu hồi mentor với toast notification.

---

## 3. Xác Thực & Kiểm Thử Hệ Thống (Verification)

| Phân hệ / Hạng mục | Lệnh thực thi | Kết quả | Trạng thái |
| :--- | :--- | :--- | :--- |
| **Backend Unit Tests** | `.\gradlew.bat :intern-and-program-service:test` | `BUILD SUCCESSFUL in 20s`, 5 task executed, pass toàn bộ tests | ✅ PASS |
| **Backend Test Suite Mentor** | `.\gradlew.bat :intern-and-program-service:test --tests "org.example.internservice.intern.service.InternProfileMentorTest"` | `BUILD SUCCESSFUL in 23s`, pass 100% case phân công & thu hồi | ✅ PASS |
| **Backend Docker** | `docker compose build --no-cache intern-and-program-service` | Image rebuilt, container `intern-and-program-service` đạt trạng thái **healthy** | ✅ HEALTHY |
| **Frontend TypeScript Build** | `npm run build` (`tsc -b && vite build`) | Built dist thành công, không còn bất kỳ lỗi compile/type check nào | ✅ PASS |
