# Implementation Plan: Story 29 — Thêm Mới Mentor Qua Token Onboarding (TM-29)

## 1. Mục Tiêu (Objective)
Triển khai tính năng **Thêm mới Mentor** cho HR theo User Story 29 theo kiến trúc **Domain Entity `MentorProfile`** trong `intern-and-program-service`, tích hợp cơ chế **Token-Based Onboarding (Zero-Knowledge Password)** và **BẮT BUỘC gửi email kích hoạt** qua `reporting-and-integration-service`.

---

## 2. Danh Sách Tệp Tác Động (Impacted Files)

### Phân hệ 1: `intern-and-program-service` (Port 8082 - Domain Master)
- `[NEW]` `MentorProfile.java`: Entity quản lý Mentor (`id`, `userId`, `fullName`, `email`, `phone`, `department_id`, `status: PENDING_ACTIVATION / ACTIVE / INACTIVE`).
- `[NEW]` `MentorProfileRepository.java`: JPA Repository với các method `existsByEmail`, `existsByPhone`, `findByUserId`, v.v.
- `[NEW]` `CreateMentorProfileRequest.java`: DTO nhận 4 trường từ HR (`fullName`, `email`, `phone`, `departmentId`).
- `[MODIFY]` `OnboardingTokenService.java`: Mở rộng sinh token nhận thêm `role: "MENTOR" | "INTERN"`.
- `[MODIFY]` `OnboardingController.java`:
  - `POST /api/onboarding/activate`: Phân nhánh logic theo role:
    - Nếu `role == "MENTOR"`: Gọi Identity Service tạo Account với role `MENTOR`, gán `userId` vào `MentorProfile`, chuyển `status = ACTIVE`.
    - Nếu `role == "INTERN"`: Giữ nguyên luồng kích hoạt Intern hiện tại.
- `[MODIFY]` `InternProfileServiceImpl.java` & `InternProfileController.java`:
  - Thêm `POST /api/interns/mentors`: Tạo `MentorProfile`, sinh token, gọi email service.
  - Thêm `POST /api/interns/mentors/{id}/resend-invitation`: Gửi lại thư mời.
  - Cập nhật `getAvailableMentors()`: Đọc trực tiếp từ `MentorProfile` JOIN `Department` (xóa bỏ 100% logic đoán mò phòng ban bằng email).

### Phân hệ 2: `reporting-and-integration-service` (Port 8083 - Mandatory Email)
- `[NEW]` `SendMentorOnboardingEmailRequest.java`: DTO nhận thông tin gửi thư mời Mentor.
- `[MODIFY]` `EmailTemplateBuilder.java`: Thêm template HTML `buildMentorOnboardingEmail()` với tone giọng chào mừng Mentor, nêu bật vai trò hướng dẫn và nút bấm kích hoạt tài khoản.
- `[MODIFY]` `EmailDeliveryService.java`: Thêm method `sendMentorOnboardingEmailAsync()`.
- `[MODIFY]` `EmailIntegrationController.java`: Thêm endpoint `POST /api/integration/emails/mentor-onboarding`.

### Phân hệ 3: `InternHub-Frontend` (Port 5173 - React + Vite)
- `[NEW]` [CreateMentorModal.tsx](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/mentors/CreateMentorModal.tsx): Form modal gồm 4 trường (`fullName`, `email`, `phone`, `departmentId`).
- `[MODIFY]` [intern.types.ts](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/types/intern.types.ts): Thêm interface `CreateMentorProfileRequest` và cập nhật `MentorOption` status enum.
- `[MODIFY]` [internService.ts](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/services/internService.ts): Thêm `createMentor()`, `resendMentorInvitation()`.
- `[MODIFY]` [HrMentorManagementPage.tsx](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/hr/mentors/HrMentorManagementPage.tsx): Nút `+ Thêm Mentor`, hiển thị badge `Chờ kích hoạt` (`PENDING_ACTIVATION`), nút gửi lại thư mời.
- `[MODIFY]` [OnboardingActivationPage.tsx](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub-Frontend/src/pages/public/OnboardingActivationPage.tsx): Nhận diện kích hoạt Mentor, hiển thị thông điệp chào mừng Mentor phù hợp.

---

## 3. Thứ Tự Triển Khai (Execution Sequence)

1. **Backend Phase 1 (Domain Model & Repository)**:
   - Tạo Entity `MentorProfile` và `MentorProfileRepository` trong `intern-and-program-service`.
2. **Backend Phase 2 (Email Service)**:
   - Viết template và endpoint gửi email thư mời kích hoạt Mentor trong `reporting-and-integration-service`.
3. **Backend Phase 3 (API & Onboarding Activation)**:
   - Hoàn thiện `POST /api/interns/mentors` và refactor `getAvailableMentors()` trong `intern-and-program-service`.
   - Cập nhật `/api/onboarding/activate` hỗ trợ kích hoạt cho Mentor.
4. **Frontend Phase 4 (UI Integration)**:
   - Tạo `CreateMentorModal.tsx` và tích hợp vào `HrMentorManagementPage.tsx`.
   - Tinh chỉnh `OnboardingActivationPage.tsx`.
5. **Testing & Verification**:
   - Chạy Gradle build & tests trên backend.
   - Chạy `npm run build` trên frontend.
