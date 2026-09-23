# Tasks: Duyệt Hoặc Từ Chối Hồ Sơ Thực Tập Sinh (Jira TM-11)

- **Jira Ticket:** [TM-11](https://robluccibn9935.atlassian.net/browse/TM-11)
- **Branch:** `feature/TM-11/approve-reject-application`
- **Spec Reference:** [TM-11-approve-reject-application-spec.md](file:///c:/Users/Luong%20Anh%20Huy/InternHub-Workspace/InternHub/docs/specs/TM-11-approve-reject-application-spec.md)

---

## Danh sách công việc chi tiết (Tasks Checklist)

### Task 1: Data Model & Entity Updates
- [x] Bổ sung các trường xét duyệt vào entity `InternProfile`:
  - `rejectionReason` (`TEXT`)
  - `reviewedBy` (`VARCHAR(100)`)
  - `reviewedAt` (`LocalDateTime`)
- [x] Bổ sung method nghiệp vụ `applyDecision(InternStatus decision, String reason, String reviewerUsername)` trong `InternProfile`.

### Task 2: DTOs & Validation
- [x] Tạo DTO `InternDecisionRequest` trong `org.example.internservice.intern.dto.request` với validation `@NotNull` cho `decision` và giới hạn ký tự cho `rejectionReason`.
- [x] Bổ sung các trường `rejectionReason`, `reviewedBy`, `reviewedAt` vào `InternResponse`.
- [x] Đảm bảo mapper `mapToResponse` trong `InternProfileServiceImpl` ánh xạ đầy đủ các trường mới này.

### Task 3: Service Layer & State Machine
- [x] Khai báo method `InternResponse processDecision(Long id, InternDecisionRequest request, String reviewerUsername);` trong interface `InternProfileService`.
- [x] Triển khai `processDecision` trong `InternProfileServiceImpl`:
  - Kiểm tra sự tồn tại của hồ sơ theo `id` (ném `ResourceNotFoundException` nếu không thấy).
  - Kiểm tra tính hợp lệ của quyết định: Chỉ chấp nhận `decision == APPROVED` hoặc `decision == REJECTED`.
  - Validate chuyển đổi trạng thái với `currentStatus.canTransitionTo(decision)`. Nếu vi phạm, ném `IllegalStateException` / `BadRequestException`.
  - Kiểm tra điều kiện lý do từ chối: Khi `decision == REJECTED`, `rejectionReason` không được rỗng và phải có ít nhất 5 ký tự sau khi `trim()`.
  - Nếu `decision == APPROVED`: Xóa `rejectionReason` (set `null`), cập nhật `status = APPROVED`.
  - Ghi nhận `reviewedBy = reviewerUsername` và `reviewedAt = LocalDateTime.now()`.
  - Lưu entity vào cơ sở dữ liệu và trả về `InternResponse`.

### Task 4: Controller Layer & Security
- [x] Bổ sung endpoint `PATCH /api/interns/{id}/decision` trong `InternProfileController`:
  - `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`
  - `@Auditable(action = AuditAction.CHANGE_INTERN_STATUS, module = AuditModule.INTERN, description = "Duyệt hoặc từ chối hồ sơ thực tập sinh")`
  - Lấy thông tin username người dùng từ `Authentication` / `SecurityContext` truyền vào service.
  - Trả về `ApiResponse.success(200, message, response)`.

### Task 5: Unit & Integration Tests
- [x] Viết các unit tests trong `InternProfileServiceTest`:
  - `processDecision_whenApproved_shouldUpdateStatusAndClearRejectionReason`
  - `processDecision_whenRejected_withValidReason_shouldUpdateStatusAndReason`
  - `processDecision_whenRejected_withEmptyReason_shouldThrowBadRequestException`
  - `processDecision_whenInvalidTransition_shouldThrowIllegalStateException`
  - `processDecision_whenInternNotFound_shouldThrowResourceNotFoundException`
- [x] Bổ sung controller test trong `InternProfileControllerTest`:
  - Cho phép Role HR/Admin duyệt thành công (`200 OK`).
  - Cho phép Role HR/Admin từ chối thành công (`200 OK`).

### Task 6: Verification & Compilation
- [x] Chạy lệnh `.\gradlew compileJava` trên module `intern-and-program-service`.
- [x] Chạy lệnh `.\gradlew test` trên module `intern-and-program-service`.
- [x] Kiểm tra tính toàn vẹn hệ thống và đối chiếu Acceptance Criteria (AC-1 đến AC-9).
