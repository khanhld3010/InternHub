# Implementation Plan: TM-16 Phân Công & Quản Lý Người Hướng Dẫn Thực Tập Sinh

## 1. Mục Tiêu (Objective)
Triển khai tính năng phân công, thay đổi và thu hồi Mentor cho Thực tập sinh (Ticket [TM-16](https://robluccibn9935.atlassian.net/browse/TM-16)) trên cả hai phân hệ Backend (`intern-and-program-service`, `identity-and-access-service`, `reporting-and-integration-service`) và Frontend (`InternHub-Frontend`), tuân thủ 100% tài liệu đặc tả [TM-16 Spec v1.3.0](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/TM-16-assign-mentor-spec.md).

---

## 2. Danh Sách Tệp Tác Động (Impacted Files)

### Backend (`InternHub/`):
- `[NEW]` [MentorAssignmentStatus.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/enums/MentorAssignmentStatus.java)
- `[NEW]` [InternMentorAssignment.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternMentorAssignment.java)
- `[NEW]` [InternMentorAssignmentRepository.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternMentorAssignmentRepository.java)
- `[NEW]` [AssignMentorRequest.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/request/AssignMentorRequest.java)
- `[NEW]` [RevokeMentorRequest.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/request/RevokeMentorRequest.java)
- `[NEW]` [MentorOptionResponse.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/MentorOptionResponse.java)
- `[NEW]` [MentorAssignmentResponse.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/MentorAssignmentResponse.java)
- `[MODIFY]` [InternProfile.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/entity/InternProfile.java) (bổ sung mentorId, mentorName, mentorEmail, needsMentorReassignment, mentorReassignmentReason)
- `[MODIFY]` [InternResponse.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/dto/response/InternResponse.java) (bổ sung các trường mentor)
- `[MODIFY]` [InternProfileRepository.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/repository/InternProfileRepository.java)
- `[MODIFY]` [InternProfileService.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/service/InternProfileService.java) & `InternProfileServiceImpl.java`
- `[MODIFY]` [InternProfileController.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/intern/controller/InternProfileController.java)
- `[MODIFY]` [ProgramLifecycleJob.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/main/java/org/example/internservice/program/job/ProgramLifecycleJob.java) (quét auto-transition sang INTERNING)
- `[NEW]` [InternProfileMentorTest.java](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/intern-and-program-service/src/test/java/org/example/internservice/intern/service/InternProfileMentorTest.java)

### Frontend (`InternHub-Frontend/`):
- `[NEW]` [AssignMentorModal.tsx](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/AssignMentorModal.tsx)
- `[NEW]` [RevokeMentorModal.tsx](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/RevokeMentorModal.tsx)
- `[MODIFY]` [intern.types.ts](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/types/intern.types.ts) (bổ sung MentorOption, MentorAssignmentResponse, AssignMentorRequest)
- `[MODIFY]` [internService.ts](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/services/internService.ts) (thêm getAvailableMentors, assignMentor, revokeMentor, getMentorHistory)
- `[MODIFY]` [HrInternTable.tsx](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/HrInternTable.tsx) (cột Mentor + badge tĩnh "Chưa có Mentor" / "Cần đổi Mentor")
- `[MODIFY]` [DetailInternModal.tsx](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/components/DetailInternModal.tsx) (khối Mentor, nút Đổi/Gỡ, timeline lịch sử)

---

## 3. Kế Hoạch Triển Khai Chi Tiết Theo 4 Bước

### Phase 1: Backend Domain Models, Repositories & DTOs
1. Tạo enum `MentorAssignmentStatus` (`ACTIVE`, `REPLACED`, `REVOKED`).
2. Mở rộng `InternProfile` với các trường mentor và 2 cờ điều phối.
3. Tạo entity `InternMentorAssignment` liên kết ManyToOne với `InternProfile`.
4. Viết các DTO request & response chuẩn hóa.

### Phase 2: Backend Business Logic & REST API Endpoints
1. Viết logic tại `InternProfileServiceImpl`:
   - Chặn nếu `needsProgramReassignment == true`.
   - Kiểm tra `replaceReason` bắt buộc khi `mentorId != null`.
   - Cập nhật bản ghi cũ sang `REPLACED` và tạo bản ghi mới `ACTIVE`.
   - Logic điều kiện kép: Chuyển `APPROVED` sang `INTERNING` khi `program.status == ONGOING`.
   - Viết API `DELETE /api/interns/{id}/mentor` thu hồi phân công sang `REVOKED`.
2. Mở rộng `ProgramLifecycleJob`: Quét chuyển tự động `APPROVED` sang `INTERNING` khi program bắt đầu chạy.
3. Controller mapping, phân quyền `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")` và ghi vết `@Auditable`.

### Phase 3: Backend Verification
1. Viết Unit Test cho Service.
2. Kiểm tra Gradle compile và test.

### Phase 4: Frontend Implementation & Integration
1. Cập nhật Types và API Service trong `internService.ts`.
2. Xây dựng modal `AssignMentorModal.tsx` và `RevokeMentorModal.tsx` với đầy đủ dynamic fields và static warnings.
3. Tích hợp vào `HrInternTable.tsx` và `DetailInternModal.tsx`.
4. Chạy `npm run build` xác minh không có lỗi type/linting.
