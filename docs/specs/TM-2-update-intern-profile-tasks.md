# Tasks: Cập Nhật Thông Tin Hồ Sơ Thực Tập Sinh (Jira TM-2)

- **Jira Ticket:** [TM-2](https://robluccibn9935.atlassian.net/browse/TM-2)
- **Branch:** `feature/TM-2/update-intern-profile`
- **Spec Reference:** [TM-2-update-intern-profile-spec.md](file:///c:/Users/Luong%20Anh%20Huy/InternHub/docs/specs/TM-2-update-intern-profile-spec.md)

---

## Danh sách công việc chi tiết (Tasks Checklist)

### Task 1: Repository Layer
- [x] Bổ sung các phương thức kiểm tra trùng lặp có loại trừ ID trong `InternProfileRepository`:
  - `boolean existsByEmailAndIdNot(String email, Long id);`
  - `boolean existsByPhoneAndIdNot(String phone, Long id);`

### Task 2: DTO & Exception Handling
- [x] Tạo DTO `UpdateInternRequest` trong `org.example.employeeservice.intern.dto.request` với đầy đủ Jakarta Validation annotations (`@NotBlank`, `@Pattern`, `@Size`, `@NotNull`).
- [x] Đảm bảo `academicYear` và `notes` là tùy chọn (`optional`).
- [x] Đảm bảo `GlobalExceptionHandler` bắt ngoại lệ `IllegalStateException` và trả về HTTP `400 Bad Request` (cho trường hợp vi phạm State Machine).

### Task 3: Security Configuration
- [x] Bật `@EnableMethodSecurity` trong `SecurityConfig` để hỗ trợ annotation `@PreAuthorize`.

### Task 4: Service Layer & State Machine Logic
- [x] Khai báo method `InternResponse updateIntern(Long id, UpdateInternRequest request);` trong `InternProfileService`.
- [x] Triển khai `updateIntern` trong `InternProfileServiceImpl`:
  - Tìm kiếm hồ sơ theo `id`, ném `ResourceNotFoundException` nếu không tìm thấy.
  - Validate ngày bắt đầu & kết thúc: `endDate >= startDate`.
  - Validate luồng chuyển đổi trạng thái State Machine theo quy tắc:
    - `PENDING` $\rightarrow$ `APPROVED`, `REJECTED`
    - `APPROVED` $\rightarrow$ `INTERNING`, `REJECTED`
    - `INTERNING` $\rightarrow$ `COMPLETED`, `REJECTED`
    - `COMPLETED` $\rightarrow$ Terminal (không cho đổi)
    - `REJECTED` $\rightarrow$ `PENDING`
    - Cho phép giữ nguyên trạng thái hiện tại.
  - Kiểm tra trùng email qua `existsByEmailAndIdNot`, ném `DuplicateResourceException`.
  - Kiểm tra trùng SĐT qua `existsByPhoneAndIdNot`, ném `DuplicateResourceException`.
  - Cập nhật thông tin vào entity, bảo toàn `id` và `internCode`.
  - Lưu và trả về DTO `InternResponse`.

### Task 5: Controller Layer
- [x] Bổ sung endpoint `@PutMapping("/{id}")` trong `InternProfileController` với `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")`.
- [x] Trả về HTTP `200 OK` bọc trong `ApiResponse<InternResponse>`.

### Task 6: Unit & Controller Tests
- [x] Bổ sung 7 Unit Tests trong `InternProfileServiceTest`:
  - `updateIntern_withValidData_shouldUpdateAndReturnResponse`
  - `updateIntern_whenIdNotFound_shouldThrowResourceNotFoundException`
  - `updateIntern_whenEmailExistsForOther_shouldThrowDuplicateResourceException`
  - `updateIntern_whenPhoneExistsForOther_shouldThrowDuplicateResourceException`
  - `updateIntern_whenEndDateBeforeStartDate_shouldThrowIllegalArgumentException`
  - `updateIntern_whenInvalidStatusTransition_shouldThrowIllegalStateException`
  - `updateIntern_whenEmailAndPhoneUnchanged_shouldUpdateSuccessfully`
- [x] Bổ sung Controller Test trong `InternProfileControllerTest`.

### Task 7: Verification & Build
- [x] Chạy `cd employee-service; .\gradlew compileJava; cd ..`
- [x] Chạy `cd employee-service; .\gradlew test; cd ..`
- [x] Chạy `cd employee-service; .\gradlew bootJar; cd ..`
- [x] Đối chiếu bằng chứng kiểm thử với toàn bộ Acceptance Criteria (AC-1 đến AC-9).
